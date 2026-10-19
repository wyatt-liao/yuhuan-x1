#include "app.hpp"
#include "esp_timer.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <sys/time.h>

namespace {
std::string encode(cJSON *object) {
  char *p = cJSON_PrintUnformatted(object);
  std::string s = p ? p : "{\"ok\":false,\"error\":\"memory\"}";
  cJSON_free(p);
  cJSON_Delete(object);
  return s;
}
std::string reply(bool ok, const char *error = "") {
  auto *o = cJSON_CreateObject();
  cJSON_AddBoolToObject(o, "ok", ok);
  if (!ok)
    cJSON_AddStringToObject(o, "error", error);
  return encode(o);
}
const char *text(cJSON *o, const char *key) {
  auto *v = cJSON_GetObjectItemCaseSensitive(o, key);
  return cJSON_IsString(v) ? v->valuestring : "";
}
double number(cJSON *o, const char *key, double fallback = 0) {
  auto *v = cJSON_GetObjectItemCaseSensitive(o, key);
  return cJSON_IsNumber(v) ? v->valuedouble : fallback;
}
void id(cJSON *o, const char *key, uint64_t value) {
  cJSON_AddStringToObject(o, key, yh::hexId(value).c_str());
}
bool action_locked(yh::ButtonEvent event) {
  auto previous = app.buttons;
  bool ok = apply_action(event);
  if (!ok)
    app.buttons = previous;
  return ok;
}
} // namespace

bool apply_action(yh::ButtonEvent e) {
  if (e.action == yh::Action::None)
    return true;
  if (!app.storageReady || app.shutdown)
    return false;
  bool ok = false;
  switch (e.action) {
  case yh::Action::Start:
    if (app.writable) {
      app.manualGroup = app.store.startManual();
      ok = app.manualGroup != 0;
    }
    break;
  case yh::Action::Resume:
    if (app.writable)
      ok = app.store.startManual(app.manualGroup) != 0;
    break;
  case yh::Action::Pause:
    ok = app.store.stopManual();
    break;
  case yh::Action::Stop:
    ok = app.store.stopManual();
    if (ok)
      app.manualGroup = 0;
    break;
  case yh::Action::Protect:
    ok = app.store.protectLast(std::min(e.hours, 24u) * 3600) != 0;
    break;
  default:
    ok = true;
    break;
  }
  if (ok)
    led_flash(0, 6, 0);
  else {
    app.writeErrors++;
    led_flash(8, 0, 0);
  }
  return ok;
}
std::string status_json() {
  std::lock_guard<std::mutex> lock(app.controlMutex);
  auto *o = cJSON_CreateObject();
  cJSON_AddBoolToObject(o, "ok", true);
  cJSON_AddStringToObject(o, "product", "Yuhuan X1");
  cJSON_AddStringToObject(o, "version", "0.1.0");
  cJSON_AddStringToObject(o, "board", "independent-mic-20261018");
  cJSON_AddBoolToObject(o, "storage", app.storageReady);
  cJSON_AddBoolToObject(o, "recording",
                        app.storageReady && app.audioRunning && app.writable &&
                            !app.shutdown);
  cJSON_AddStringToObject(
      o, "manual",
      app.buttons.state() == yh::ManualState::Recording ? "recording"
      : app.buttons.state() == yh::ManualState::Paused  ? "paused"
                                                        : "idle");
  cJSON_AddBoolToObject(o, "quad_ready", app.quad);
  cJSON_AddNumberToObject(o, "selected_mic", app.selectedMic + 1);
  cJSON_AddNumberToObject(o, "battery_mv", app.batteryMv);
  cJSON_AddNumberToObject(o, "free_bytes", double(app.freeBytes));
  cJSON_AddNumberToObject(o, "queue_drops", app.queueDrops);
  cJSON_AddNumberToObject(o, "dma_overruns", app.dmaOverruns);
  cJSON_AddNumberToObject(o, "write_errors", app.writeErrors);
  id(o, "timeline_samples", app.store.timeline());
  cJSON_AddNumberToObject(o, "epoch_ms", double(epoch_ms()));
  cJSON_AddStringToObject(o, "store_error", app.store.error().c_str());
  cJSON_AddStringToObject(o, "wifi_ip", wifi_ip().c_str());
  cJSON_AddStringToObject(o, "wifi_token", wifi_token().c_str());
  cJSON_AddNumberToObject(o, "sample_rate", yh::kRate);
  cJSON_AddStringToObject(o, "codec", "ima_adpcm_wav");
  return encode(o);
}
std::string manifest_json(uint64_t after, size_t limit) {
  auto *o = cJSON_CreateObject();
  cJSON_AddBoolToObject(o, "ok", true);
  auto *a = cJSON_AddArrayToObject(o, "segments");
  for (auto &s : app.store.list(after, std::min<size_t>(limit, 16))) {
    auto *x = cJSON_CreateObject();
    id(x, "id", s.id);
    id(x, "start", s.start);
    cJSON_AddNumberToObject(x, "samples", s.samples);
    cJSON_AddNumberToObject(x, "epoch_ms", double(s.epochMs));
    cJSON_AddNumberToObject(x, "bytes", s.bytes);
    cJSON_AddBoolToObject(x, "protected", s.protectedAudio);
    cJSON_AddBoolToObject(x, "synced", s.synced);
    cJSON_AddItemToArray(a, x);
  }
  return encode(o);
}
std::string command_json(const char *input) {
  cJSON *o = cJSON_Parse(input);
  if (!cJSON_IsObject(o)) {
    cJSON_Delete(o);
    return reply(false, "invalid JSON");
  }
  std::string cmd = text(o, "cmd"), result;
  uint64_t value = 0;
  if (cmd == "status")
    result = status_json();
  else if (cmd == "list") {
    const char *a = text(o, "after");
    if (*a && !yh::parseId(a, value))
      result = reply(false, "invalid after");
    else
      result = manifest_json(
          value, size_t(std::clamp(number(o, "limit", 16), 1., 16.)));
  } else if (cmd == "marks") {
    auto *r = cJSON_CreateObject();
    cJSON_AddBoolToObject(r, "ok", true);
    auto *a = cJSON_AddArrayToObject(r, "marks");
    for (auto &m : app.store.marks()) {
      auto *x = cJSON_CreateObject();
      id(x, "id", m.id);
      id(x, "start", m.start);
      id(x, "end", m.end);
      id(x, "group", m.group);
      cJSON_AddNumberToObject(x, "kind", m.kind);
      cJSON_AddItemToArray(a, x);
    }
    result = encode(r);
  } else if (cmd == "ack") {
    double n = number(o, "bytes", -1);
    bool valid = yh::parseId(text(o, "id"), value) && n >= 0 &&
                 n <= UINT32_MAX && std::floor(n) == n;
    result = reply(valid && app.store.acknowledge(value, uint32_t(n)),
                   "ACK size or id mismatch");
  } else if (cmd == "unprotect")
    result =
        reply(yh::parseId(text(o, "id"), value) && app.store.releaseMark(value),
              "unknown mark or storage error");
  else if (cmd == "time") {
    double ms = number(o, "epoch_ms");
    bool valid = ms >= 1577836800000. && ms < 4102444800000.;
    if (valid) {
      timeval tv{time_t(ms / 1000), suseconds_t(std::fmod(ms, 1000) * 1000)};
      valid = settimeofday(&tv, nullptr) == 0;
    }
    result = reply(valid, "invalid epoch_ms");
  } else if (cmd == "wifi") {
    const char *ssid = text(o, "ssid");
    bool ok = true;
    if (*ssid)
      ok = wifi_configure(ssid, text(o, "password"));
    auto *enabled = cJSON_GetObjectItemCaseSensitive(o, "enabled");
    if (ok)
      wifi_enable(!cJSON_IsFalse(enabled));
    result = reply(ok, "invalid Wi-Fi configuration");
  } else if (cmd == "protect") {
    unsigned hours = unsigned(std::clamp(number(o, "hours", 1), 1., 24.));
    std::lock_guard<std::mutex> lock(app.controlMutex);
    result = reply(action_locked({yh::Action::Protect, hours}),
                   "no audio or storage error");
  } else if (cmd == "button1" || cmd == "button2") {
    std::lock_guard<std::mutex> lock(app.controlMutex);
    auto previous = app.buttons;
    auto e = app.buttons.click(cmd == "button1" ? 1 : 2,
                               esp_timer_get_time() / 1000);
    bool ok = apply_action(e);
    if (!ok)
      app.buttons = previous;
    result = reply(ok, "button action failed");
  } else if (cmd == "manual") {
    std::lock_guard<std::mutex> lock(app.controlMutex);
    auto previous = app.buttons;
    std::string mode = text(o, "action");
    yh::Action a = yh::Action::None;
    auto state = previous.state();
    if (mode == "start" && state == yh::ManualState::Idle)
      a = yh::Action::Start;
    if (mode == "stop" && state != yh::ManualState::Idle)
      a = yh::Action::Stop;
    if (mode == "pause" && state == yh::ManualState::Recording)
      a = yh::Action::Pause;
    if (mode == "resume" && state == yh::ManualState::Paused)
      a = yh::Action::Resume;
    bool ok = a != yh::Action::None && apply_action({a, 0});
    if (ok)
      app.buttons.reset(a == yh::Action::Stop    ? yh::ManualState::Idle
                        : a == yh::Action::Pause ? yh::ManualState::Paused
                                                 : yh::ManualState::Recording);
    result = reply(ok, "invalid manual state or storage error");
  } else
    result = reply(false, "unknown command");
  cJSON_Delete(o);
  return result;
}
