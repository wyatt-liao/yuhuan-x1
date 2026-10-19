#include "yh/codec.hpp"
#include <algorithm>
#include <cstring>

namespace yh {
void put16(uint8_t *p, uint16_t v) {
  p[0] = v;
  p[1] = v >> 8;
}
void put32(uint8_t *p, uint32_t v) {
  for (int i = 0; i < 4; ++i)
    p[i] = v >> (8 * i);
}
void put64(uint8_t *p, uint64_t v) {
  for (int i = 0; i < 8; ++i)
    p[i] = v >> (8 * i);
}
uint32_t get32(const uint8_t *p) {
  uint32_t v = 0;
  for (int i = 0; i < 4; ++i)
    v |= uint32_t(p[i]) << (8 * i);
  return v;
}
uint64_t get64(const uint8_t *p) {
  uint64_t v = 0;
  for (int i = 0; i < 8; ++i)
    v |= uint64_t(p[i]) << (8 * i);
  return v;
}
uint32_t crc32(const uint8_t *data, size_t n) {
  uint32_t crc = ~0u;
  for (size_t i = 0; i < n; ++i) {
    crc ^= data[i];
    for (int b = 0; b < 8; ++b)
      crc = (crc >> 1) ^ (0xedb88320u & (0u - (crc & 1u)));
  }
  return ~crc;
}
std::array<uint8_t, kWavHeader> wavHeader(uint32_t samples, uint32_t bytes) {
  std::array<uint8_t, kWavHeader> h{};
  std::memcpy(h.data(), "RIFF", 4);
  put32(h.data() + 4, 52 + bytes);
  std::memcpy(h.data() + 8, "WAVEfmt ", 8);
  put32(h.data() + 16, 20);
  put16(h.data() + 20, 0x11);
  put16(h.data() + 22, 1);
  put32(h.data() + 24, kRate);
  put32(h.data() + 28, kRate * kBlockBytes / kBlockSamples);
  put16(h.data() + 32, kBlockBytes);
  put16(h.data() + 34, 4);
  put16(h.data() + 36, 2);
  put16(h.data() + 38, kBlockSamples);
  std::memcpy(h.data() + 40, "fact", 4);
  put32(h.data() + 44, 4);
  put32(h.data() + 48, samples);
  std::memcpy(h.data() + 52, "data", 4);
  put32(h.data() + 56, bytes);
  return h;
}
bool validHeader(const uint8_t *h) {
  return !std::memcmp(h, "RIFF", 4) && !std::memcmp(h + 8, "WAVEfmt ", 8) &&
         get32(h + 16) == 20 && h[20] == 0x11 && h[21] == 0 && h[22] == 1 &&
         h[23] == 0 && get32(h + 24) == kRate && h[32] == 0 && h[33] == 1 &&
         h[34] == 4 && h[38] == (kBlockSamples & 255) &&
         h[39] == (kBlockSamples >> 8) && !std::memcmp(h + 40, "fact", 4) &&
         !std::memcmp(h + 52, "data", 4);
}
void ImaEncoder::encode(const int16_t *pcm, size_t count, uint8_t *out) {
  static constexpr int steps[89] = {
      7,     8,     9,     10,    11,    12,    13,    14,    16,    17,
      19,    21,    23,    25,    28,    31,    34,    37,    41,    45,
      50,    55,    60,    66,    73,    80,    88,    97,    107,   118,
      130,   143,   157,   173,   190,   209,   230,   253,   279,   307,
      337,   371,   408,   449,   494,   544,   598,   658,   724,   796,
      876,   963,   1060,  1166,  1282,  1411,  1552,  1707,  1878,  2066,
      2272,  2499,  2749,  3024,  3327,  3660,  4026,  4428,  4871,  5358,
      5894,  6484,  7132,  7845,  8630,  9493,  10442, 11487, 12635, 13899,
      15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};
  static constexpr int adjust[16] = {-1, -1, -1, -1, 2, 4, 6, 8,
                                     -1, -1, -1, -1, 2, 4, 6, 8};
  int predictor = count ? pcm[0] : 0;
  std::memset(out, 0, kBlockBytes);
  put16(out, uint16_t(predictor));
  out[2] = uint8_t(index_);
  for (size_t i = 1; i < kBlockSamples; ++i) {
    const int sample = i < count ? pcm[i] : predictor;
    int diff = sample - predictor;
    int nibble = 0;
    if (diff < 0) {
      nibble = 8;
      diff = -diff;
    }
    int step = steps[index_];
    int delta = step >> 3;
    if (diff >= step) {
      nibble |= 4;
      diff -= step;
      delta += step;
    }
    if (diff >= (step >> 1)) {
      nibble |= 2;
      diff -= step >> 1;
      delta += step >> 1;
    }
    if (diff >= (step >> 2)) {
      nibble |= 1;
      delta += step >> 2;
    }
    predictor =
        std::clamp(predictor + ((nibble & 8) ? -delta : delta), -32768, 32767);
    index_ = std::clamp(index_ + adjust[nibble], 0, 88);
    out[4 + (i - 1) / 2] |= uint8_t(nibble << (((i - 1) & 1) * 4));
  }
}
} // namespace yh
