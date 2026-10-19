#include "app.hpp"
#include "esp_event.h"
#include "esp_http_server.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "nvs.h"
#include <algorithm>
#include <cstring>

namespace {
std::mutex mutex;
std::string token, ip;
wifi_config_t config{};
httpd_handle_t server = nullptr;
std::atomic<bool> connected{false}, desired{false}, running{false};
std::atomic<int64_t> touched{0};
bool configured = false;
bool authorized(httpd_req_t *req) {
  char value[100]{};
  if (httpd_req_get_hdr_value_str(req, "Authorization", value, sizeof(value)) !=
          ESP_OK ||
      std::string(value) != "Bearer " + token) {
    httpd_resp_send_err(req, HTTPD_401_UNAUTHORIZED, "Bearer token required");
    return false;
  }
  wifi_touch();
  return true;
}
esp_err_t json_reply(httpd_req_t *r, const std::string &s) {
  httpd_resp_set_type(r, "application/json");
  httpd_resp_set_hdr(r, "Cache-Control", "no-store");
  return httpd_resp_send(r, s.data(), s.size());
}
bool query(httpd_req_t *r, char *output, size_t size) {
  return httpd_req_get_url_query_str(r, output, size) == ESP_OK;
}
esp_err_t status(httpd_req_t *r) {
  return authorized(r) ? json_reply(r, status_json()) : ESP_OK;
}
esp_err_t segments(httpd_req_t *r) {
  if (!authorized(r))
    return ESP_OK;
  char q[128]{}, id[20]{};
  uint64_t after = 0;
  if (query(r, q, sizeof(q)) &&
      httpd_query_key_value(q, "after", id, sizeof(id)) == ESP_OK &&
      !yh::parseId(id, after))
    return httpd_resp_send_err(r, HTTPD_400_BAD_REQUEST, "invalid after");
  return json_reply(r, manifest_json(after, 16));
}
esp_err_t control(httpd_req_t *r) {
  if (!authorized(r))
    return ESP_OK;
  if (r->content_len <= 0 || r->content_len > 512)
    return httpd_resp_send_err(r, HTTPD_400_BAD_REQUEST, "body length");
  char body[513]{};
  int done = 0;
  while (done < r->content_len) {
    int n = httpd_req_recv(r, body + done, r->content_len - done);
    if (n <= 0)
      return ESP_FAIL;
    done += n;
  }
  return json_reply(r, command_json(body));
}
esp_err_t audio(httpd_req_t *r) {
  if (!authorized(r))
    return ESP_OK;
  char q[96]{}, id[20]{};
  uint64_t key = 0;
  yh::Segment s;
  if (!query(r, q, sizeof(q)) ||
      httpd_query_key_value(q, "id", id, sizeof(id)) != ESP_OK ||
      !yh::parseId(id, key) || !app.store.lease(key))
    return httpd_resp_send_err(r, HTTPD_404_NOT_FOUND, "segment unavailable");
  struct Lease {
    uint64_t id;
    ~Lease() { app.store.unlease(id); }
  } lease{key};
  if (!app.store.lookup(key, s))
    return httpd_resp_send_err(r, HTTPD_404_NOT_FOUND, "segment unavailable");
  uint32_t offset = 0;
  char range[64]{};
  if (httpd_req_get_hdr_value_str(r, "Range", range, sizeof(range)) == ESP_OK) {
    unsigned start = 0;
    int consumed = 0;
    if (std::sscanf(range, "bytes=%u-%n", &start, &consumed) != 1 ||
        !consumed || range[consumed] != 0 || start >= s.bytes)
      return httpd_resp_send_err(r, HTTPD_400_BAD_REQUEST,
                                 "only bytes=OFFSET- supported");
    offset = start;
    char contentRange[80];
    std::snprintf(contentRange, sizeof(contentRange), "bytes %lu-%lu/%lu",
                  (unsigned long)offset, (unsigned long)s.bytes - 1,
                  (unsigned long)s.bytes);
    httpd_resp_set_status(r, "206 Partial Content");
    httpd_resp_set_hdr(r, "Content-Range", contentRange);
  }
  httpd_resp_set_type(r, "audio/wav");
  httpd_resp_set_hdr(r, "Accept-Ranges", "bytes");
  httpd_resp_set_hdr(r, "Cache-Control", "no-store");
  uint8_t buffer[4096];
  while (offset < s.bytes && !app.shutdown) {
    int n =
        app.store.read(key, offset, buffer,
                       std::min<uint32_t>(sizeof(buffer), s.bytes - offset));
    if (n <= 0)
      return ESP_FAIL;
    if (httpd_resp_send_chunk(r, reinterpret_cast<char *>(buffer), n) != ESP_OK)
      return ESP_FAIL;
    offset += n;
    wifi_touch();
    vTaskDelay(1);
  }
  return httpd_resp_send_chunk(r, nullptr, 0);
}
void event(void *, esp_event_base_t base, int32_t event, void *data) {
  if (base == WIFI_EVENT && event == WIFI_EVENT_STA_DISCONNECTED) {
    connected = false;
    {
      std::lock_guard<std::mutex> lock(mutex);
      ip.clear();
    }
    if (desired)
      esp_wifi_connect();
  }
  if (base == IP_EVENT && event == IP_EVENT_STA_GOT_IP) {
    auto *got = static_cast<ip_event_got_ip_t *>(data);
    char value[24];
    std::snprintf(value, sizeof(value), IPSTR, IP2STR(&got->ip_info.ip));
    {
      std::lock_guard<std::mutex> lock(mutex);
      ip = value;
    }
    connected = true;
    esp_wifi_set_ps(WIFI_PS_MIN_MODEM);
  }
}
void manager(void *) {
  while (!app.shutdown) {
    bool want = desired && esp_timer_get_time() - touched <
                               CONFIG_YH_WIFI_IDLE_SECONDS * 1000000ll;
    if (want && !running) {
      std::lock_guard<std::mutex> lock(mutex);
      if (configured && esp_wifi_set_config(WIFI_IF_STA, &config) == ESP_OK &&
          esp_wifi_start() == ESP_OK) {
        running = true;
        esp_wifi_connect();
      }
    }
    if (!want && running) {
      desired = false;
      running = false;
      esp_wifi_disconnect();
      esp_wifi_stop();
      connected = false;
    }
    vTaskDelay(pdMS_TO_TICKS(1000));
  }
  vTaskDelete(nullptr);
}
} // namespace
void wifi_touch() { touched = esp_timer_get_time(); }
bool wifi_connected() { return connected; }
std::string wifi_ip() {
  std::lock_guard<std::mutex> lock(mutex);
  return ip;
}
const std::string &wifi_token() { return token; }
void wifi_enable(bool enable) {
  if (enable)
    wifi_touch();
  desired = enable;
}
bool wifi_configure(const char *ssid, const char *password) {
  size_t sn = std::strlen(ssid), pn = std::strlen(password);
  if (!sn || sn > 32 || pn > 63 || (pn > 0 && pn < 8))
    return false;
  std::lock_guard<std::mutex> lock(mutex);
  config = {};
  std::memcpy(config.sta.ssid, ssid, sn);
  std::memcpy(config.sta.password, password, pn);
  config.sta.threshold.authmode = pn ? WIFI_AUTH_WPA2_PSK : WIFI_AUTH_OPEN;
  nvs_handle_t n;
  if (nvs_open("yhx1", NVS_READWRITE, &n) != ESP_OK)
    return false;
  bool ok = nvs_set_blob(n, "wifi", &config, sizeof(config)) == ESP_OK &&
            nvs_commit(n) == ESP_OK;
  nvs_close(n);
  configured = ok;
  if (running) {
    esp_wifi_disconnect();
    esp_wifi_set_config(WIFI_IF_STA, &config);
    esp_wifi_connect();
  }
  return ok;
}
void wifi_start() {
  ESP_ERROR_CHECK(esp_netif_init());
  ESP_ERROR_CHECK(esp_event_loop_create_default());
  esp_netif_create_default_wifi_sta();
  wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
  ESP_ERROR_CHECK(esp_wifi_init(&init));
  ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
  ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
  ESP_ERROR_CHECK(esp_event_handler_register(
      WIFI_EVENT, WIFI_EVENT_STA_DISCONNECTED, event, nullptr));
  ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP,
                                             event, nullptr));
  nvs_handle_t n;
  if (nvs_open("yhx1", NVS_READWRITE, &n) == ESP_OK) {
    size_t size = sizeof(config);
    configured = nvs_get_blob(n, "wifi", &config, &size) == ESP_OK &&
                 size == sizeof(config);
    char text[65]{};
    size = sizeof(text);
    if (nvs_get_str(n, "token", text, &size) == ESP_OK)
      token = text;
    if (token.size() != 64) {
      uint8_t random[32];
      esp_fill_random(random, sizeof(random));
      char hex[65]{};
      for (size_t i = 0; i < 32; ++i)
        std::snprintf(hex + i * 2, 3, "%02x", random[i]);
      token = hex;
      nvs_set_str(n, "token", token.c_str());
      nvs_commit(n);
    }
    nvs_close(n);
  }
  if (token.empty()) {
    ESP_LOGE("wifi", "no token; Wi-Fi disabled");
    return;
  }
  httpd_config_t http = HTTPD_DEFAULT_CONFIG();
  http.stack_size = 8192;
  http.max_uri_handlers = 5;
  http.lru_purge_enable = true;
  if (httpd_start(&server, &http) == ESP_OK) {
    httpd_uri_t u{};
    u.method = HTTP_GET;
    u.uri = "/v1/status";
    u.handler = status;
    httpd_register_uri_handler(server, &u);
    u.uri = "/v1/segments";
    u.handler = segments;
    httpd_register_uri_handler(server, &u);
    u.uri = "/v1/audio";
    u.handler = audio;
    httpd_register_uri_handler(server, &u);
    u.method = HTTP_POST;
    u.uri = "/v1/control";
    u.handler = control;
    httpd_register_uri_handler(server, &u);
  }
  if (configured)
    wifi_enable(true);
  xTaskCreate(manager, "wifi_manager", 4096, nullptr, 3, nullptr);
}
