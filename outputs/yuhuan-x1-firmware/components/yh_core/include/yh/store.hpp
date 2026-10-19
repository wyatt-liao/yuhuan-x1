#pragma once
#include "yh/codec.hpp"
#include <cstdio>
#include <map>
#include <mutex>
#include <string>
#include <vector>

namespace yh {
struct Segment {
  uint64_t id = 0, start = 0, epochMs = 0;
  uint32_t samples = 0, bytes = 0;
  bool protectedAudio = false, synced = false;
  std::string path;
  uint64_t end() const { return start + samples; }
};
struct Mark {
  uint64_t id = 0, start = 0, end = 0, group = 0;
  uint32_t kind = 0;
};
class Store {
public:
  explicit Store(std::string root, uint32_t segmentSeconds = 60,
                 uint32_t retentionSeconds = 86400);
  ~Store();
  bool mount();
  bool append(const int16_t *pcm, size_t samples, uint64_t epochMs = 0);
  bool checkpoint();
  uint64_t protectLast(uint32_t seconds = 3600);
  uint64_t startManual(uint64_t group = 0);
  bool stopManual();
  bool isManual();
  bool releaseMark(uint64_t id);
  std::vector<Mark> marks();
  std::vector<Segment> list(uint64_t after = 0, size_t limit = 16);
  bool lookup(uint64_t id, Segment &result);
  bool acknowledge(uint64_t id, uint32_t size);
  bool lease(uint64_t id);
  void unlease(uint64_t id);
  int read(uint64_t id, uint32_t offset, uint8_t *output, size_t capacity);
  bool prune();
  uint64_t timeline();
  std::string error();
  bool healthy();

private:
  bool openUnlocked(uint64_t epochMs);
  bool blockUnlocked();
  bool checkpointUnlocked();
  bool syncUnlocked();
  bool markWriteUnlocked(const Mark &m);
  bool stopManualUnlocked();
  bool protectedUnlocked(const Segment &s);
  bool pruneUnlocked();
  std::string markPath(uint64_t id);
  std::string root_, error_;
  std::mutex mutex_;
  std::map<uint64_t, Segment> segments_;
  std::map<uint64_t, Mark> marks_;
  std::map<uint64_t, unsigned> leases_;
  uint64_t now_ = 0, nextId_ = 1, nextMark_ = 1, manualId_ = 0;
  uint64_t retentionSamples_, segmentSamples_;
  bool damagedMarks_ = false;
  FILE *file_ = nullptr;
  Segment active_;
  std::array<int16_t, kBlockSamples> pending_{};
  size_t pendingCount_ = 0;
  uint32_t lastSyncedSamples_ = 0;
  ImaEncoder encoder_;
};
std::string hexId(uint64_t id);
bool parseId(const char *text, uint64_t &id);
} // namespace yh
