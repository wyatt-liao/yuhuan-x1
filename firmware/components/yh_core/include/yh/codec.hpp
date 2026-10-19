#pragma once
#include <array>
#include <cstddef>
#include <cstdint>

namespace yh {
constexpr uint32_t kRate = 16000;
constexpr size_t kBlockBytes = 256;
constexpr size_t kBlockSamples = (kBlockBytes - 4) * 2 + 1;
constexpr size_t kWavHeader = 60;
uint32_t crc32(const uint8_t *data, size_t n);
void put16(uint8_t *p, uint16_t v);
void put32(uint8_t *p, uint32_t v);
void put64(uint8_t *p, uint64_t v);
uint32_t get32(const uint8_t *p);
uint64_t get64(const uint8_t *p);
std::array<uint8_t, kWavHeader> wavHeader(uint32_t samples, uint32_t bytes);
bool validHeader(const uint8_t *p);
class ImaEncoder {
public:
  void encode(const int16_t *pcm, size_t count, uint8_t *out);

private:
  int index_ = 0;
};
} // namespace yh
