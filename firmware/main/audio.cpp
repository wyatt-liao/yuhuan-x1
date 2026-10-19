#include "app.hpp"
#include "driver/gpio.h"
#include "driver/i2s_pdm.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_rom_gpio.h"
#include "esp_timer.h"
extern "C" {
#include "fvad.h"
}
#include "soc/gpio_sig_map.h"
#include <algorithm>
#include <cmath>

static i2s_chan_handle_t rx = nullptr;
static StaticQueue_t queueControl;
static uint8_t *queueStorage = nullptr;
static void auxiliary_clocks(bool on) {
  for (int pin : PIN_MIC_AUX_CLOCK) {
    gpio_set_level(static_cast<gpio_num_t>(pin), 0);
    gpio_set_direction(static_cast<gpio_num_t>(pin), GPIO_MODE_OUTPUT);
    // IDF's PDM RX driver uses m_rx_ws_sig, NOT m_rx_bck_sig.
    // All four pads are fed by the same hardware output when enabled.
    esp_rom_gpio_connect_out_signal(
        pin, on ? I2S0I_WS_OUT_IDX : SIG_GPIO_OUT_IDX, false, false);
  }
}
static bool IRAM_ATTR overflow(i2s_chan_handle_t, i2s_event_data_t *, void *) {
  app.dmaOverruns.fetch_add(1, std::memory_order_relaxed);
  return false;
}
static void capture(void *) {
  auto *raw = static_cast<int16_t *>(
      heap_caps_malloc(AUDIO_SAMPLES * 4 * sizeof(int16_t),
                       MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT));
  auto *vad = fvad_new();
  if (!raw || !vad) {
    if (vad)
      fvad_free(vad);
    heap_caps_free(raw);
    ESP_LOGE("audio", "audio memory allocation failed");
    app.writeErrors++;
    vTaskDelete(nullptr);
    return;
  }
  fvad_set_sample_rate(vad, yh::kRate);
  fvad_set_mode(vad, 2);
  app.audioRunning = true;
  std::array<int16_t, AUDIO_SAMPLES> primary{};
  yh::VoicePolicy voice;
  std::array<double, 4> noise{10000, 10000, 10000, 10000};
  unsigned selected = 0;
  uint64_t sequence = 0;
  unsigned hold = 0;
  bool clocksOn = false;
  int64_t readyAt = 0;
  while (!app.shutdown) {
    size_t got = 0;
    esp_err_t err = i2s_channel_read(
        rx, raw, AUDIO_SAMPLES * 4 * sizeof(int16_t), &got, 1000);
    if (err != ESP_OK || got != AUDIO_SAMPLES * 4 * sizeof(int16_t)) {
      if (!app.shutdown)
        app.dmaOverruns++;
      continue;
    }
    for (size_t i = 0; i < AUDIO_SAMPLES; ++i)
      primary[i] = raw[i * 4];
    int speech = fvad_process(vad, primary.data(), AUDIO_SAMPLES);
    bool requested = voice.update(speech == 1);
    if (requested && !clocksOn) {
      auxiliary_clocks(true);
      clocksOn = true;
      readyAt = esp_timer_get_time() + 300000;
    }
    // Keep MIC1 uninterrupted while auxiliary microphones and decimators
    // settle.
    bool quad = requested && esp_timer_get_time() >= readyAt;
    app.quad = quad;
    unsigned target = 0;
    if (quad) {
      std::array<double, 4> energy{};
      double best = -1;
      for (size_t c = 0; c < 4; ++c) {
        for (size_t i = 0; i < AUDIO_SAMPLES; ++i) {
          double s = raw[i * 4 + c];
          energy[c] += s * s;
        }
        energy[c] /= AUDIO_SAMPLES;
        if (speech == 0)
          noise[c] = noise[c] * 0.98 + energy[c] * 0.02;
        double score = energy[c] / std::max(noise[c], 100.0);
        if (score > best) {
          best = score;
          target = c;
        }
      }
      // Hold selection for >=300ms, then crossfade to avoid switching clicks.
      if (hold) {
        target = selected;
        --hold;
      } else if (target != selected)
        hold = 10;
    } else {
      double energy = 0;
      for (auto s : primary)
        energy += double(s) * s;
      noise[0] = noise[0] * 0.98 + (energy / AUDIO_SAMPLES) * 0.02;
    }
    AudioFrame frame;
    frame.sequence = sequence++;
    frame.epochMs = epoch_ms();
    for (size_t i = 0; i < AUDIO_SAMPLES; ++i) {
      int32_t sample = raw[i * 4 + target];
      if (target != selected)
        sample = (int32_t(raw[i * 4 + selected]) * int32_t(AUDIO_SAMPLES - i) +
                  sample * int32_t(i)) /
                 AUDIO_SAMPLES;
      frame.pcm[i] = int16_t(sample);
    }
    selected = target;
    if (!requested && clocksOn) {
      auxiliary_clocks(false);
      clocksOn = false;
    }
    app.selectedMic = selected;
    app.frames++;
    if (xQueueSend(app.audioQueue, &frame, 0) != pdTRUE)
      app.queueDrops++;
  }
  auxiliary_clocks(false);
  app.audioRunning = false;
  fvad_free(vad);
  heap_caps_free(raw);
  vTaskDelete(nullptr);
}
bool audio_start() {
  constexpr size_t depth = 64;
  queueStorage = static_cast<uint8_t *>(heap_caps_malloc(
      depth * sizeof(AudioFrame), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
  if (!queueStorage)
    return false;
  app.audioQueue = xQueueCreateStatic(depth, sizeof(AudioFrame), queueStorage,
                                      &queueControl);
  i2s_chan_config_t channel =
      I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0, I2S_ROLE_MASTER);
  channel.dma_desc_num = 12;
  channel.dma_frame_num = 240;
  if (i2s_new_channel(&channel, nullptr, &rx) != ESP_OK)
    return false;
  i2s_pdm_rx_config_t config{};
  config.clk_cfg = I2S_PDM_RX_CLK_DEFAULT_CONFIG(yh::kRate);
  config.slot_cfg = I2S_PDM_RX_SLOT_PCM_FMT_DEFAULT_CONFIG(
      I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO);
  config.slot_cfg.slot_mask = static_cast<i2s_pdm_slot_mask_t>(
      I2S_PDM_RX_LINE0_SLOT_LEFT | I2S_PDM_RX_LINE1_SLOT_RIGHT |
      I2S_PDM_RX_LINE2_SLOT_LEFT | I2S_PDM_RX_LINE3_SLOT_RIGHT);
  config.gpio_cfg.clk = static_cast<gpio_num_t>(PIN_PDM_CLK);
  for (auto &pin : config.gpio_cfg.dins)
    pin = GPIO_NUM_NC;
  for (size_t i = 0; i < PIN_MIC_DATA.size(); ++i)
    config.gpio_cfg.dins[i] = static_cast<gpio_num_t>(PIN_MIC_DATA[i]);
  auxiliary_clocks(false);
  if (i2s_channel_init_pdm_rx_mode(rx, &config) != ESP_OK)
    return false;
  i2s_event_callbacks_t callbacks{};
  callbacks.on_recv_q_ovf = overflow;
  if (i2s_channel_register_event_callback(rx, &callbacks, nullptr) != ESP_OK ||
      i2s_channel_enable(rx) != ESP_OK)
    return false;
  return xTaskCreatePinnedToCore(capture, "pdm_capture", 6144, nullptr, 20,
                                 nullptr, 1) == pdPASS;
}
void audio_stop() {
  if (rx)
    i2s_channel_disable(rx);
}
