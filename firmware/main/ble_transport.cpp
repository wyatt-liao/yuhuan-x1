#include "app.hpp"
#include "esp_log.h"
#include "esp_timer.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include <algorithm>
#include <cstring>

extern "C" void ble_store_config_init(void);
namespace {
// Human-readable UUIDs: 8d4a0001/0002/0003-5c61-4d3d-a34b-e271e6fa1001.
const ble_uuid128_t service =
    BLE_UUID128_INIT(0x01, 0x10, 0xfa, 0xe6, 0x71, 0xe2, 0x4b, 0xa3, 0x3d, 0x4d,
                     0x61, 0x5c, 0x01, 0x00, 0x4a, 0x8d);
const ble_uuid128_t control =
    BLE_UUID128_INIT(0x01, 0x10, 0xfa, 0xe6, 0x71, 0xe2, 0x4b, 0xa3, 0x3d, 0x4d,
                     0x61, 0x5c, 0x02, 0x00, 0x4a, 0x8d);
const ble_uuid128_t data =
    BLE_UUID128_INIT(0x01, 0x10, 0xfa, 0xe6, 0x71, 0xe2, 0x4b, 0xa3, 0x3d, 0x4d,
                     0x61, 0x5c, 0x03, 0x00, 0x4a, 0x8d);
uint16_t notifyHandle = 0;
uint8_t ownType = 0;
std::atomic<uint16_t> connection{BLE_HS_CONN_HANDLE_NONE};
std::atomic<uint32_t> generation{0};
std::atomic<bool> subscribed{false};
struct Job {
  uint16_t connection;
  uint32_t generation;
  char json[513];
};
QueueHandle_t jobs = nullptr;
char incoming[513]{};
size_t incomingSize = 0;
bool active(const Job &j) {
  return j.connection == connection && j.generation == generation && subscribed;
}
bool encrypted(uint16_t handle) {
  ble_gap_conn_desc d{};
  return ble_gap_conn_find(handle, &d) == 0 && d.sec_state.encrypted;
}
void put16(uint8_t *p, uint16_t v) {
  p[0] = v;
  p[1] = v >> 8;
}
void put32(uint8_t *p, uint32_t v) {
  for (unsigned i = 0; i < 4; ++i)
    p[i] = v >> (8 * i);
}
bool send(const Job &job, uint8_t kind, uint16_t request, uint32_t offset,
          const uint8_t *bytes, size_t length, bool error = false) {
  size_t done = 0;
  do {
    if (!active(job) || !encrypted(job.connection))
      return false;
    size_t mtu = ble_att_mtu(job.connection);
    if (mtu < 23)
      return false;
    size_t n = std::min(length - done, mtu - 3 - 12);
    uint8_t packet[260];
    packet[0] = kind;
    packet[1] = (done + n == length ? 1 : 0) | (error ? 2 : 0);
    put16(packet + 2, request);
    put32(packet + 4, offset + done);
    put32(packet + 8, yh::crc32(bytes ? bytes + done : nullptr, n));
    if (n)
      std::memcpy(packet + 12, bytes + done, n);
    bool sent = false;
    for (unsigned retry = 0; retry < 100 && active(job); ++retry) {
      auto *mbuf = ble_hs_mbuf_from_flat(packet, n + 12);
      if (mbuf &&
          ble_gatts_notify_custom(job.connection, notifyHandle, mbuf) == 0) {
        sent = true;
        break;
      }
      vTaskDelay(pdMS_TO_TICKS(10));
    }
    if (!sent)
      return false;
    done += n;
    vTaskDelay(1);
  } while (done < length);
  return true;
}
void worker(void *) {
  Job job{};
  uint8_t buffer[4096];
  uint64_t leased = 0;
  uint32_t leaseGeneration = 0;
  int64_t lastRead = 0;
  while (!app.shutdown) {
    if (leased && (leaseGeneration != generation ||
                   esp_timer_get_time() - lastRead > 120000000)) {
      app.store.unlease(leased);
      leased = 0;
    }
    if (xQueueReceive(jobs, &job, pdMS_TO_TICKS(1000)) != pdTRUE)
      continue;
    if (!active(job) || !encrypted(job.connection))
      continue;
    auto *o = cJSON_Parse(job.json);
    auto *r = cJSON_GetObjectItemCaseSensitive(o, "req");
    uint16_t req = cJSON_IsNumber(r) ? uint16_t(r->valueint) : 0;
    auto *cmd = cJSON_GetObjectItemCaseSensitive(o, "cmd");
    if (cJSON_IsString(cmd) && std::strcmp(cmd->valuestring, "read") == 0) {
      auto *id = cJSON_GetObjectItemCaseSensitive(o, "id");
      auto *off = cJSON_GetObjectItemCaseSensitive(o, "offset");
      auto *len = cJSON_GetObjectItemCaseSensitive(o, "length");
      uint64_t key = 0;
      bool valid = cJSON_IsString(id) && yh::parseId(id->valuestring, key) &&
                   cJSON_IsNumber(off) && off->valuedouble >= 0 &&
                   off->valuedouble <= UINT32_MAX && cJSON_IsNumber(len) &&
                   len->valuedouble >= 1 && len->valuedouble <= sizeof(buffer);
      int n = -1;
      if (valid) {
        if (leased != key) {
          if (leased)
            app.store.unlease(leased);
          leased = app.store.lease(key) ? key : 0;
          leaseGeneration = job.generation;
        }
        if (leased) {
          lastRead = esp_timer_get_time();
          n = app.store.read(key, uint32_t(off->valuedouble), buffer,
                             size_t(len->valuedouble));
        }
      }
      if (n >= 0)
        send(job, 2, req, uint32_t(off->valuedouble), buffer, size_t(n));
      else {
        const char *error = "{\"ok\":false,\"error\":\"segment unavailable or "
                            "invalid offset\"}";
        send(job, 1, req, 0, reinterpret_cast<const uint8_t *>(error),
             std::strlen(error), true);
      }
    } else {
      auto reply = command_json(job.json);
      send(job, 1, req, 0, reinterpret_cast<const uint8_t *>(reply.data()),
           reply.size());
      if (cJSON_IsString(cmd) && std::strcmp(cmd->valuestring, "ack") == 0 &&
          leased) {
        app.store.unlease(leased);
        leased = 0;
      }
    }
    cJSON_Delete(o);
  }
  if (leased)
    app.store.unlease(leased);
  vTaskDelete(nullptr);
}
int access(uint16_t handle, uint16_t, ble_gatt_access_ctxt *context, void *) {
  if (context->op != BLE_GATT_ACCESS_OP_WRITE_CHR)
    return BLE_ATT_ERR_WRITE_NOT_PERMITTED;
  if (!encrypted(handle))
    return BLE_ATT_ERR_INSUFFICIENT_ENC;
  uint16_t length = OS_MBUF_PKTLEN(context->om);
  if (length > 512)
    return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
  char part[513]{};
  if (ble_hs_mbuf_to_flat(context->om, part, 512, nullptr) != 0)
    return BLE_ATT_ERR_UNLIKELY;
  for (size_t i = 0; i < length; ++i) {
    if (part[i] == '\n') {
      if (incomingSize) {
        Job job{};
        job.connection = handle;
        job.generation = generation;
        std::memcpy(job.json, incoming, incomingSize);
        incomingSize = 0;
        if (xQueueSend(jobs, &job, 0) != pdTRUE)
          return BLE_ATT_ERR_INSUFFICIENT_RES;
      }
    } else {
      if (incomingSize >= 512) {
        incomingSize = 0;
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
      }
      incoming[incomingSize++] = part[i];
    }
  }
  return 0;
}
void advertise();
int gap(ble_gap_event *event, void *) {
  switch (event->type) {
  case BLE_GAP_EVENT_CONNECT:
    incomingSize = 0;
    if (event->connect.status == 0) {
      connection = event->connect.conn_handle;
      generation++;
      subscribed = false;
      ble_gap_security_initiate(connection);
    } else
      advertise();
    break;
  case BLE_GAP_EVENT_DISCONNECT:
    connection = BLE_HS_CONN_HANDLE_NONE;
    generation++;
    subscribed = false;
    incomingSize = 0;
    advertise();
    break;
  case BLE_GAP_EVENT_SUBSCRIBE:
    if (event->subscribe.attr_handle == notifyHandle)
      subscribed = event->subscribe.cur_notify;
    break;
  case BLE_GAP_EVENT_REPEAT_PAIRING:
    ble_gap_terminate(event->repeat_pairing.conn_handle,
                      BLE_ERR_REM_USER_CONN_TERM);
    return BLE_GAP_REPEAT_PAIRING_IGNORE;
  default:
    break;
  }
  return 0;
}
void advertise() {
  ble_hs_adv_fields fields{};
  fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
  fields.uuids128 = &service;
  fields.num_uuids128 = 1;
  fields.uuids128_is_complete = 1;
  if (ble_gap_adv_set_fields(&fields) != 0)
    return;
  ble_hs_adv_fields scan{};
  const char *name = "Yuhuan X1";
  scan.name = reinterpret_cast<const uint8_t *>(name);
  scan.name_len = std::strlen(name);
  scan.name_is_complete = 1;
  ble_gap_adv_rsp_set_fields(&scan);
  ble_gap_adv_params params{};
  params.conn_mode = BLE_GAP_CONN_MODE_UND;
  params.disc_mode = BLE_GAP_DISC_MODE_GEN;
  params.itvl_min = 1600;
  params.itvl_max = 1760;
  ble_gap_adv_start(ownType, nullptr, BLE_HS_FOREVER, &params, gap, nullptr);
}
void on_sync() {
  if (ble_hs_util_ensure_addr(0) == 0 && ble_hs_id_infer_auto(0, &ownType) == 0)
    advertise();
}
void host(void *) {
  nimble_port_run();
  nimble_port_freertos_deinit();
}
ble_gatt_chr_def chars[3]{};
ble_gatt_svc_def services[2]{};
} // namespace
void ble_start() {
  jobs = xQueueCreate(6, sizeof(Job));
  if (!jobs)
    return;
  if (nimble_port_init() != ESP_OK) {
    ESP_LOGE("ble", "NimBLE init failed");
    return;
  }
  ble_svc_gap_init();
  ble_svc_gatt_init();
  ble_svc_gap_device_name_set("Yuhuan X1");
  chars[0].uuid = &control.u;
  chars[0].access_cb = access;
  chars[0].flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_ENC;
  chars[1].uuid = &data.u;
  chars[1].access_cb = access;
  chars[1].flags = BLE_GATT_CHR_F_NOTIFY;
  chars[1].val_handle = &notifyHandle;
  services[0].type = BLE_GATT_SVC_TYPE_PRIMARY;
  services[0].uuid = &service.u;
  services[0].characteristics = chars;
  if (ble_gatts_count_cfg(services) != 0 || ble_gatts_add_svcs(services) != 0) {
    ESP_LOGE("ble", "GATT registration failed");
    return;
  }
  ble_hs_cfg.sync_cb = on_sync;
  ble_hs_cfg.sm_io_cap = BLE_HS_IO_NO_INPUT_OUTPUT;
  ble_hs_cfg.sm_bonding = 1;
  ble_hs_cfg.sm_sc = 1;
  ble_hs_cfg.sm_mitm = 0;
  ble_hs_cfg.sm_our_key_dist =
      BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
  ble_hs_cfg.sm_their_key_dist =
      BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
  ble_store_config_init();
  xTaskCreate(worker, "ble_commands", 8192, nullptr, 4, nullptr);
  nimble_port_freertos_init(host);
}
