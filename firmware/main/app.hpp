#pragma once
#include "cJSON.h"
#include "esp_err.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "yh/buttons.hpp"
#include "yh/store.hpp"
#include <array>
#include <atomic>
#include <string>

constexpr int PIN_PDM_CLK = 4, PIN_PDM_DIN0 = 6, PIN_PDM_DIN1 = 7;
constexpr std::array<int, 4> PIN_MIC_DATA{6, 5, 7, 8};
constexpr std::array<int, 3> PIN_MIC_AUX_CLOCK{16, 17, 18};
constexpr int PIN_SD_CS = 10, PIN_SD_MOSI = 11, PIN_SD_CLK = 12,
              PIN_SD_MISO = 13;
constexpr int PIN_BUTTON1 = 0, PIN_LED = 48, PIN_BATTERY = 1;
constexpr size_t AUDIO_SAMPLES = 480;
struct AudioFrame {
  uint64_t sequence = 0, epochMs = 0;
  std::array<int16_t, AUDIO_SAMPLES> pcm{};
};
struct App {
  yh::Store store{"/sdcard/yhx1"};
  yh::Buttons buttons;
  std::mutex controlMutex;
  std::atomic<bool> storageReady{false}, writable{true}, quad{false},
      shutdown{false};
  std::atomic<uint32_t> batteryMv{0}, queueDrops{0}, dmaOverruns{0},
      selectedMic{0}, writeErrors{0};
  std::atomic<uint64_t> frames{0}, freeBytes{0};
  std::atomic<bool> audioRunning{false}, writerStopped{true};
  QueueHandle_t audioQueue = nullptr;
  uint64_t manualGroup = 0;
};
extern App app;
uint64_t epoch_ms();
bool audio_start();
void audio_stop();
void led_flash(uint8_t r, uint8_t g, uint8_t b);
bool apply_action(yh::ButtonEvent event);
std::string status_json();
std::string command_json(const char *text);
std::string manifest_json(uint64_t after, size_t limit);
void ble_start();
void wifi_start();
bool wifi_configure(const char *ssid, const char *password);
void wifi_enable(bool enable);
bool wifi_connected();
std::string wifi_ip();
const std::string &wifi_token();
void wifi_touch();
