#include "app.hpp"
#include "driver/gpio.h"
#include "driver/rmt_encoder.h"
#include "driver/rmt_tx.h"
#include "driver/spi_common.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_pm.h"
#include "esp_sleep.h"
#include "esp_timer.h"
#include "esp_vfs_fat.h"
#include "nvs_flash.h"
#include "sdmmc_cmd.h"
#include <cstring>
#include <sys/time.h>

App app;
static rmt_channel_handle_t led = nullptr;
static rmt_encoder_handle_t ledEncoder = nullptr;
static adc_oneshot_unit_handle_t adc = nullptr;
static adc_cali_handle_t calibration = nullptr;
uint64_t epoch_ms() {
  timeval tv{};
  gettimeofday(&tv, nullptr);
  return tv.tv_sec >= 1577836800
             ? uint64_t(tv.tv_sec) * 1000 + tv.tv_usec / 1000
             : 0;
}
static void led_write(uint8_t r, uint8_t g, uint8_t b) {
  if (!led)
    return;
  rmt_symbol_word_t symbols[25]{};
  uint32_t rgb = (uint32_t(g) << 16) | (uint32_t(r) << 8) | b;
  for (int i = 0; i < 24; ++i) {
    bool one = rgb & (1u << (23 - i));
    symbols[i].level0 = 1;
    symbols[i].duration0 = one ? 9 : 3;
    symbols[i].level1 = 0;
    symbols[i].duration1 = one ? 3 : 9;
  }
  symbols[24].level0 = 0;
  symbols[24].duration0 = 400;
  symbols[24].level1 = 0;
  symbols[24].duration1 = 400;
  rmt_transmit_config_t tx{};
  if (rmt_transmit(led, ledEncoder, symbols, sizeof(symbols), &tx) == ESP_OK)
    rmt_tx_wait_all_done(led, 100);
}
void led_flash(uint8_t r, uint8_t g, uint8_t b) {
  led_write(r, g, b);
  vTaskDelay(pdMS_TO_TICKS(60));
  led_write(0, 0, 0);
}
static bool sd_mount() {
  sdmmc_host_t host = SDSPI_HOST_DEFAULT();
  host.max_freq_khz = CONFIG_YH_SD_SPI_MHZ * 1000;
  spi_bus_config_t bus{};
  bus.mosi_io_num = PIN_SD_MOSI;
  bus.miso_io_num = PIN_SD_MISO;
  bus.sclk_io_num = PIN_SD_CLK;
  bus.quadwp_io_num = -1;
  bus.quadhd_io_num = -1;
  bus.max_transfer_sz = 16384;
  if (spi_bus_initialize(static_cast<spi_host_device_t>(host.slot), &bus,
                         SPI_DMA_CH_AUTO) != ESP_OK)
    return false;
  sdspi_device_config_t slot = SDSPI_DEVICE_CONFIG_DEFAULT();
  slot.gpio_cs = static_cast<gpio_num_t>(PIN_SD_CS);
  slot.host_id = static_cast<spi_host_device_t>(host.slot);
  esp_vfs_fat_sdmmc_mount_config_t config{};
  config.format_if_mount_failed = false;
  config.max_files = 8;
  config.allocation_unit_size = 32768;
  sdmmc_card_t *card = nullptr;
  esp_err_t err =
      esp_vfs_fat_sdspi_mount("/sdcard", &host, &slot, &config, &card);
  if (err != ESP_OK) {
    ESP_LOGE("sd", "mount failed: %s; card was NOT formatted",
             esp_err_to_name(err));
    return false;
  }
  sdmmc_card_print_info(stdout, card);
  return app.store.mount();
}
static void writer(void *) {
  AudioFrame frame;
  std::array<int16_t, AUDIO_SAMPLES> silence{};
  uint64_t expected = 0;
  while (!app.shutdown) {
    if (xQueueReceive(app.audioQueue, &frame, pdMS_TO_TICKS(100)) != pdTRUE)
      continue;
    if (app.shutdown)
      break;
    if (app.writable) {
      // Queue loss is counted and represented by silence, never hidden as
      // a shorter timeline. The health endpoint reports every occurrence.
      for (uint64_t n = expected; n < frame.sequence; ++n)
        if (!app.store.append(silence.data(), silence.size(), 0)) {
          app.writeErrors++;
          app.writable = false;
          break;
        }
      if (app.writable && !app.store.append(frame.pcm.data(), frame.pcm.size(),
                                            frame.epochMs)) {
        app.writeErrors++;
        app.writable = false;
      }
    }
    expected = frame.sequence + 1;
  }
  app.store.checkpoint();
  app.writerStopped = true;
  vTaskDelete(nullptr);
}
static void battery_init() {
  adc_oneshot_unit_init_cfg_t unit{};
  unit.unit_id = ADC_UNIT_1;
  if (adc_oneshot_new_unit(&unit, &adc) != ESP_OK) {
    adc = nullptr;
    return;
  }
  adc_oneshot_chan_cfg_t config{};
  config.atten = ADC_ATTEN_DB_12;
  config.bitwidth = ADC_BITWIDTH_DEFAULT;
  adc_oneshot_config_channel(adc, ADC_CHANNEL_0, &config);
  adc_cali_curve_fitting_config_t cal{};
  cal.unit_id = ADC_UNIT_1;
  cal.chan = ADC_CHANNEL_0;
  cal.atten = ADC_ATTEN_DB_12;
  cal.bitwidth = ADC_BITWIDTH_DEFAULT;
  if (adc_cali_create_scheme_curve_fitting(&cal, &calibration) != ESP_OK)
    calibration = nullptr;
}
static uint32_t battery_read() {
  if (!adc || !calibration)
    return 0;
  int64_t sum = 0;
  unsigned count = 0;
  for (int i = 0; i < 16; ++i) {
    int raw = 0, mv = 0;
    if (adc_oneshot_read(adc, ADC_CHANNEL_0, &raw) == ESP_OK &&
        adc_cali_raw_to_voltage(calibration, raw, &mv) == ESP_OK) {
      sum += mv;
      ++count;
    }
  }
  // R11/R12 = 100k/100k on the inspected PCB. This is voltage, not fuel-gauge
  // SOC.
  return count ? uint32_t(sum * 2 / count) : 0;
}
static void health(void *) {
  unsigned low = 0;
  while (!app.shutdown) {
    app.batteryMv = battery_read();
    if (app.storageReady) {
      uint64_t total = 0, free = 0;
      if (esp_vfs_fat_info("/sdcard", &total, &free) == ESP_OK) {
        app.freeBytes = free;
        if (free < 64ull * 1024 * 1024) {
          app.store.prune();
          app.writable = false;
        }
      }
    }
    uint32_t mv = app.batteryMv;
    low = (mv > 2000 && mv < CONFIG_YH_LOW_BATTERY_MV) ? low + 1 : 0;
    ESP_LOGI("health",
             "battery=%lumV free=%llu quad=%d queue_drop=%lu dma_overrun=%lu "
             "write_error=%lu",
             (unsigned long)mv, (unsigned long long)app.freeBytes.load(),
             app.quad.load(), (unsigned long)app.queueDrops.load(),
             (unsigned long)app.dmaOverruns.load(),
             (unsigned long)app.writeErrors.load());
    if (low >= 3) {
      app.shutdown = true;
      audio_stop();
      while (!app.writerStopped)
        vTaskDelay(pdMS_TO_TICKS(20));
      app.store.stopManual();
      app.store.checkpoint();
      led_flash(8, 0, 0);
      esp_sleep_enable_ext0_wakeup(static_cast<gpio_num_t>(PIN_BUTTON1), 0);
      esp_deep_sleep_start();
    }
    vTaskDelay(pdMS_TO_TICKS(30000));
  }
  vTaskDelete(nullptr);
}
static void buttons(void *) {
  const int pins[2] = {PIN_BUTTON1, CONFIG_YH_BUTTON2_GPIO};
  bool previous[2] = {false, false}, stable[2] = {false, false};
  uint64_t changed[2] = {0, 0};
  for (int pin : pins)
    if (pin >= 0) {
      gpio_config_t c{};
      c.pin_bit_mask = 1ull << pin;
      c.mode = GPIO_MODE_INPUT;
      c.pull_up_en = GPIO_PULLUP_ENABLE;
      gpio_config(&c);
    }
  while (!app.shutdown) {
    uint64_t ms = esp_timer_get_time() / 1000;
    for (unsigned i = 0; i < 2; ++i)
      if (pins[i] >= 0) {
        bool down = gpio_get_level(static_cast<gpio_num_t>(pins[i])) == 0;
        if (down != previous[i]) {
          previous[i] = down;
          changed[i] = ms;
        }
        if (ms - changed[i] >= 30 && down != stable[i]) {
          stable[i] = down;
          if (!down) {
            std::lock_guard<std::mutex> lock(app.controlMutex);
            auto before = app.buttons;
            if (!apply_action(app.buttons.click(i + 1, ms)))
              app.buttons = before;
          }
        }
      }
    {
      std::lock_guard<std::mutex> lock(app.controlMutex);
      apply_action(app.buttons.tick(ms));
    }
    vTaskDelay(pdMS_TO_TICKS(10));
  }
  vTaskDelete(nullptr);
}
extern "C" void app_main() {
  esp_err_t err = nvs_flash_init();
  if (err == ESP_ERR_NVS_NO_FREE_PAGES ||
      err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
    ESP_ERROR_CHECK(nvs_flash_erase());
    err = nvs_flash_init();
  }
  ESP_ERROR_CHECK(err);
  esp_pm_config_t pm{};
  pm.max_freq_mhz = 160;
  pm.min_freq_mhz = 80;
  pm.light_sleep_enable = false;
  ESP_ERROR_CHECK(esp_pm_configure(&pm));
  rmt_tx_channel_config_t lc{};
  lc.gpio_num = static_cast<gpio_num_t>(PIN_LED);
  lc.clk_src = RMT_CLK_SRC_DEFAULT;
  lc.resolution_hz = 10000000;
  lc.mem_block_symbols = 64;
  lc.trans_queue_depth = 1;
  if (rmt_new_tx_channel(&lc, &led) == ESP_OK) {
    rmt_copy_encoder_config_t ec{};
    ESP_ERROR_CHECK(rmt_new_copy_encoder(&ec, &ledEncoder));
    ESP_ERROR_CHECK(rmt_enable(led));
    led_write(0, 0, 0);
  }
  app.storageReady = sd_mount();
  battery_init();
  wifi_start();
  ble_start();
  if (app.storageReady && audio_start()) {
    app.writerStopped = false;
    if (xTaskCreatePinnedToCore(writer, "audio_writer", 6144, nullptr, 10,
                                nullptr, 0) != pdPASS) {
      app.writerStopped = true;
      app.writable = false;
      app.shutdown = true;
      audio_stop();
      app.writeErrors++;
    }
  } else {
    app.writeErrors++;
    app.writable = false;
    ESP_LOGE("x1", "recording unavailable; inspect status and SD card");
  }
  if (CONFIG_YH_BUTTON2_GPIO < 0)
    ESP_LOGW("x1", "Second function button disabled by configuration");
  xTaskCreate(buttons, "buttons", 4096, nullptr, 5, nullptr);
  xTaskCreate(health, "health", 4096, nullptr, 3, nullptr);
  ESP_LOGI("x1", "Yuhuan X1 v0.1.0 started; continuous background recording, "
                 "no cloud dependency");
}
