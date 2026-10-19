#include "yh/store.hpp"
#include <algorithm>
#include <cerrno>
#include <climits>
#include <cstring>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

namespace yh {
std::string hexId(uint64_t id) {
  char b[17];
  std::snprintf(b, sizeof(b), "%016llx", (unsigned long long)id);
  return b;
}
bool parseId(const char *text, uint64_t &id) {
  if (!text || std::strlen(text) != 16)
    return false;
  id = 0;
  for (int i = 0; i < 16; ++i) {
    char c = text[i];
    int n = c >= '0' && c <= '9'   ? c - '0'
            : c >= 'a' && c <= 'f' ? c - 'a' + 10
                                   : -1;
    if (n < 0)
      return false;
    id = (id << 4) | uint64_t(n);
  }
  return id > 0;
}
static bool writeFile(const std::string &path, const uint8_t *data,
                      size_t size) {
  // Files are immutable: the destination must not already exist.
  std::string tmp = path + ".tmp";
  FILE *f = std::fopen(tmp.c_str(), "wb");
  if (!f)
    return false;
  bool ok = std::fwrite(data, 1, size, f) == size;
  ok = (std::fflush(f) == 0) && ok;
  ok = (::fsync(::fileno(f)) == 0) && ok;
  ok = (std::fclose(f) == 0) && ok;
  if (ok)
    ok = ::rename(tmp.c_str(), path.c_str()) == 0;
  return ok;
}
Store::Store(std::string root, uint32_t segmentSeconds,
             uint32_t retentionSeconds)
    : root_(std::move(root)),
      retentionSamples_(uint64_t(retentionSeconds) * kRate),
      segmentSamples_(uint64_t(segmentSeconds) * kRate) {}
Store::~Store() { checkpoint(); }
std::string Store::markPath(uint64_t id) {
  return root_ + "/marks/m" + hexId(id) + ".mark";
}
bool Store::mount() {
  std::lock_guard<std::mutex> lock(mutex_);
  if (file_)
    return false;
  segments_.clear();
  marks_.clear();
  leases_.clear();
  error_.clear();
  now_ = 0;
  nextId_ = nextMark_ = 1;
  manualId_ = 0;
  damagedMarks_ = false;
  for (const auto &d : {root_, root_ + "/audio", root_ + "/marks"}) {
    if (::mkdir(d.c_str(), 0775) != 0 && errno != EEXIST) {
      error_ = "mkdir_failed";
      return false;
    }
  }
  DIR *dir = ::opendir((root_ + "/audio").c_str());
  if (!dir) {
    error_ = "audio_directory_unreadable";
    return false;
  }
  while (auto *e = ::readdir(dir)) {
    std::string name = e->d_name;
    if (name.size() != 55 || name[0] != 's' || name[17] != '_' ||
        name[34] != '_')
      continue;
    bool part = name.substr(51) == "part", sealed = name.substr(51) == ".wav";
    // Names: s<id>_<sample-start>_<epoch-ms>.wav or .prt (same length).
    part = name.substr(51) == ".prt";
    if (!part && !sealed)
      continue;
    uint64_t id = 0, start = 0, epoch = 0;
    std::string a = name.substr(1, 16), b = name.substr(18, 16),
                c = name.substr(35, 16);
    if (!parseId(a.c_str(), id))
      continue;
    auto parseZero = [](const std::string &t, uint64_t &v) {
      if (t == "0000000000000000") {
        v = 0;
        return true;
      }
      return parseId(t.c_str(), v);
    };
    if (!parseZero(b, start) || !parseZero(c, epoch))
      continue;
    std::string path = root_ + "/audio/" + name;
    FILE *f = std::fopen(path.c_str(), part ? "rb+" : "rb");
    if (!f) {
      error_ = "segment_unreadable";
      continue;
    }
    std::array<uint8_t, kWavHeader> h{};
    struct stat st{};
    bool ok = ::fstat(::fileno(f), &st) == 0 && st.st_size >= int(kWavHeader) &&
              std::fread(h.data(), 1, h.size(), f) == h.size() &&
              validHeader(h.data());
    if (!ok) {
      std::fclose(f);
      error_ = "corrupt_segment_preserved";
      continue;
    }
    uint32_t bytes = uint32_t(st.st_size - kWavHeader);
    uint32_t whole = bytes / kBlockBytes * kBlockBytes;
    uint32_t samples = get32(h.data() + 48);
    if (part) {
      if (whole == 0) {
        std::fclose(f);
        ::unlink(path.c_str());
        continue;
      }
      if (bytes != whole || get32(h.data() + 56) != whole || samples == 0 ||
          samples > whole / kBlockBytes * kBlockSamples)
        samples = whole / kBlockBytes * kBlockSamples;
      auto repaired = wavHeader(samples, whole);
      ok = ::ftruncate(::fileno(f), kWavHeader + whole) == 0 &&
           std::fseek(f, 0, SEEK_SET) == 0 &&
           std::fwrite(repaired.data(), 1, repaired.size(), f) ==
               repaired.size();
      ok = (std::fflush(f) == 0) && ok;
      ok = (::fsync(::fileno(f)) == 0) && ok;
      std::fclose(f);
      std::string target = path.substr(0, path.size() - 4) + ".wav";
      if (ok)
        ok = ::rename(path.c_str(), target.c_str()) == 0;
      if (!ok) {
        error_ = "recovery_failed";
        continue;
      }
      path = target;
      bytes = whole;
    } else {
      std::fclose(f);
      if (bytes != whole || get32(h.data() + 56) != bytes || !samples ||
          samples > whole / kBlockBytes * kBlockSamples) {
        error_ = "corrupt_segment_preserved";
        continue;
      }
    }
    Segment s{id,    start, epoch, samples, uint32_t(kWavHeader + bytes),
              false, false, path};
    s.synced = ::access((path + ".ack").c_str(), F_OK) == 0;
    segments_[id] = s;
    now_ = std::max(now_, s.end());
    nextId_ = std::max(nextId_, id + 1);
  }
  ::closedir(dir);
  dir = ::opendir((root_ + "/marks").c_str());
  if (!dir) {
    error_ = "marks_directory_unreadable";
    return false;
  }
  while (auto *e = ::readdir(dir)) {
    std::string name = e->d_name;
    if (name.size() != 22 || name[0] != 'm' || name.substr(17) != ".mark")
      continue;
    uint64_t id;
    if (!parseId(name.substr(1, 16).c_str(), id)) {
      damagedMarks_ = true;
      continue;
    }
    nextMark_ = std::max(nextMark_, id + 1);
    uint8_t b[40]{};
    FILE *f = std::fopen(markPath(id).c_str(), "rb");
    bool ok = f && std::fread(b, 1, sizeof(b), f) == sizeof(b);
    if (f)
      std::fclose(f);
    if (!ok || get32(b) != 0x314d4859 || get32(b + 36) != crc32(b, 36) ||
        get64(b + 16) < get64(b + 8)) {
      damagedMarks_ = true;
      continue;
    }
    Mark m{id, get64(b + 8), get64(b + 16), get64(b + 24), get32(b + 4)};
    if (m.end == UINT64_MAX) {
      uint8_t stop[16]{};
      f = std::fopen((markPath(id) + ".end").c_str(), "rb");
      if (f) {
        ok = std::fread(stop, 1, 16, f) == 16;
        std::fclose(f);
        if (!ok || get32(stop) != 0x31454859 ||
            get32(stop + 12) != crc32(stop, 12) || get64(stop + 4) < m.start) {
          damagedMarks_ = true;
          continue;
        }
        m.end = get64(stop + 4);
      } else {
        // A reset closes an unfinished manual recording at the last recovered
        // sample.
        m.end = std::max(m.start, now_);
        put32(stop, 0x31454859);
        put64(stop + 4, m.end);
        put32(stop + 12, crc32(stop, 12));
        if (!writeFile(markPath(id) + ".end", stop, 16)) {
          damagedMarks_ = true;
          continue;
        }
      }
    }
    marks_[id] = m;
  }
  ::closedir(dir);
  if (damagedMarks_)
    error_ = "damaged_mark_deletion_disabled";
  return true;
}
bool Store::openUnlocked(uint64_t epochMs) {
  if (file_)
    return true;
  active_ = {nextId_++, now_,  epochMs, 0, uint32_t(kWavHeader),
             false,     false, {}};
  active_.path = root_ + "/audio/s" + hexId(active_.id) + "_" + hexId(now_) +
                 "_" + hexId(epochMs) + ".prt";
  file_ = std::fopen(active_.path.c_str(), "wb+");
  if (!file_) {
    error_ = "open_failed";
    return false;
  }
  std::setvbuf(file_, nullptr, _IOFBF, 16384);
  auto h = wavHeader(0, 0);
  if (std::fwrite(h.data(), 1, h.size(), file_) != h.size()) {
    error_ = "write_failed";
    return false;
  }
  pendingCount_ = 0;
  lastSyncedSamples_ = 0;
  return true;
}
bool Store::blockUnlocked() {
  uint8_t block[kBlockBytes];
  encoder_.encode(pending_.data(), pendingCount_, block);
  if (std::fwrite(block, 1, sizeof(block), file_) != sizeof(block)) {
    error_ = "write_failed";
    return false;
  }
  active_.bytes += kBlockBytes;
  pendingCount_ = 0;
  return true;
}
bool Store::syncUnlocked() {
  auto h = wavHeader(active_.samples - uint32_t(pendingCount_),
                     active_.bytes - kWavHeader);
  bool ok = std::fseek(file_, 0, SEEK_SET) == 0 &&
            std::fwrite(h.data(), 1, h.size(), file_) == h.size() &&
            std::fseek(file_, 0, SEEK_END) == 0;
  ok = (std::fflush(file_) == 0) && ok;
  ok = (::fsync(::fileno(file_)) == 0) && ok;
  if (!ok)
    error_ = "sync_failed";
  lastSyncedSamples_ = active_.samples;
  return ok;
}
bool Store::append(const int16_t *pcm, size_t count, uint64_t epochMs) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (!pcm || !count || !openUnlocked(epochMs))
    return false;
  for (size_t i = 0; i < count; ++i) {
    pending_[pendingCount_++] = pcm[i];
    ++active_.samples;
    ++now_;
    if (pendingCount_ == kBlockSamples && !blockUnlocked())
      return false;
    if (active_.samples >= segmentSamples_) {
      if (!checkpointUnlocked() || !pruneUnlocked())
        return false;
      if (i + 1 < count && !openUnlocked(epochMs + (i + 1) * 1000 / kRate))
        return false;
    }
  }
  return !file_ || active_.samples - lastSyncedSamples_ < kRate ||
         syncUnlocked();
}
bool Store::checkpointUnlocked() {
  if (!file_)
    return true;
  if (pendingCount_ && !blockUnlocked())
    return false;
  bool ok = syncUnlocked();
  ok = (std::fclose(file_) == 0) && ok;
  file_ = nullptr;
  if (!ok)
    return false;
  std::string target = active_.path.substr(0, active_.path.size() - 4) + ".wav";
  if (::rename(active_.path.c_str(), target.c_str()) != 0) {
    error_ = "seal_failed";
    return false;
  }
  active_.path = target;
  segments_[active_.id] = active_;
  return true;
}
bool Store::checkpoint() {
  std::lock_guard<std::mutex> l(mutex_);
  return checkpointUnlocked();
}
bool Store::markWriteUnlocked(const Mark &m) {
  uint8_t b[40]{};
  put32(b, 0x314d4859);
  put32(b + 4, m.kind);
  put64(b + 8, m.start);
  put64(b + 16, m.end);
  put64(b + 24, m.group);
  put32(b + 36, crc32(b, 36));
  if (!writeFile(markPath(m.id), b, sizeof(b))) {
    error_ = "mark_write_failed";
    return false;
  }
  marks_[m.id] = m;
  return true;
}
uint64_t Store::protectLast(uint32_t seconds) {
  std::lock_guard<std::mutex> l(mutex_);
  uint64_t span = uint64_t(seconds) * kRate;
  Mark m{nextMark_++, now_ > span ? now_ - span : 0, now_, 0, 1};
  m.group = m.id;
  // Persist protection before sealing; a reset cannot erase the requested
  // history.
  if (!markWriteUnlocked(m) || !checkpointUnlocked())
    return 0;
  return m.id;
}
uint64_t Store::startManual(uint64_t group) {
  std::lock_guard<std::mutex> l(mutex_);
  if (manualId_)
    return marks_[manualId_].group;
  Mark m{nextMark_++, now_, UINT64_MAX, group, 2};
  if (!m.group)
    m.group = m.id;
  if (!markWriteUnlocked(m))
    return 0;
  manualId_ = m.id;
  return m.group;
}
bool Store::stopManualUnlocked() {
  if (!manualId_)
    return true;
  uint8_t b[16]{};
  put32(b, 0x31454859);
  put64(b + 4, now_);
  put32(b + 12, crc32(b, 12));
  if (!writeFile(markPath(manualId_) + ".end", b, 16)) {
    error_ = "manual_stop_failed";
    return false;
  }
  marks_[manualId_].end = now_;
  manualId_ = 0;
  return checkpointUnlocked();
}
bool Store::stopManual() {
  std::lock_guard<std::mutex> l(mutex_);
  return stopManualUnlocked();
}
bool Store::isManual() {
  std::lock_guard<std::mutex> l(mutex_);
  return manualId_ != 0;
}
bool Store::releaseMark(uint64_t id) {
  std::lock_guard<std::mutex> l(mutex_);
  if (id == manualId_ || !marks_.count(id))
    return false;
  if (::unlink(markPath(id).c_str()) != 0)
    return false;
  ::unlink((markPath(id) + ".end").c_str());
  marks_.erase(id);
  return true;
}
std::vector<Mark> Store::marks() {
  std::lock_guard<std::mutex> l(mutex_);
  std::vector<Mark> v;
  for (auto &p : marks_)
    v.push_back(p.second);
  return v;
}
bool Store::protectedUnlocked(const Segment &s) {
  if (damagedMarks_)
    return true;
  for (const auto &p : marks_)
    if (s.start < p.second.end && s.end() > p.second.start)
      return true;
  return false;
}
std::vector<Segment> Store::list(uint64_t after, size_t limit) {
  std::lock_guard<std::mutex> l(mutex_);
  std::vector<Segment> v;
  for (auto it = segments_.upper_bound(after);
       it != segments_.end() && v.size() < limit; ++it) {
    auto s = it->second;
    s.protectedAudio = protectedUnlocked(s);
    v.push_back(s);
  }
  return v;
}
bool Store::lookup(uint64_t id, Segment &result) {
  std::lock_guard<std::mutex> l(mutex_);
  auto i = segments_.find(id);
  if (i == segments_.end())
    return false;
  result = i->second;
  result.protectedAudio = protectedUnlocked(result);
  return true;
}
bool Store::acknowledge(uint64_t id, uint32_t size) {
  std::lock_guard<std::mutex> l(mutex_);
  auto it = segments_.find(id);
  if (it == segments_.end() || size != it->second.bytes)
    return false;
  if (it->second.synced)
    return true;
  uint8_t b[12];
  put64(b, id);
  put32(b + 8, size);
  if (!writeFile(it->second.path + ".ack", b, sizeof(b)))
    return false;
  it->second.synced = true;
  return true;
}
bool Store::lease(uint64_t id) {
  std::lock_guard<std::mutex> l(mutex_);
  if (!segments_.count(id))
    return false;
  ++leases_[id];
  return true;
}
void Store::unlease(uint64_t id) {
  std::lock_guard<std::mutex> l(mutex_);
  auto i = leases_.find(id);
  if (i != leases_.end() && --i->second == 0)
    leases_.erase(i);
}
int Store::read(uint64_t id, uint32_t offset, uint8_t *output,
                size_t capacity) {
  std::lock_guard<std::mutex> l(mutex_);
  auto i = segments_.find(id);
  if (i == segments_.end() || offset > i->second.bytes)
    return -1;
  FILE *f = std::fopen(i->second.path.c_str(), "rb");
  if (!f)
    return -1;
  int n = -1;
  if (std::fseek(f, offset, SEEK_SET) == 0) {
    n = int(std::fread(
        output, 1, std::min(capacity, size_t(i->second.bytes - offset)), f));
    if (std::ferror(f))
      n = -1;
  }
  std::fclose(f);
  return n;
}
bool Store::pruneUnlocked() {
  if (damagedMarks_)
    return true;
  uint64_t cutoff = now_ > retentionSamples_ ? now_ - retentionSamples_ : 0;
  for (auto it = segments_.begin(); it != segments_.end();) {
    if (it->second.end() <= cutoff && !protectedUnlocked(it->second) &&
        !leases_.count(it->first)) {
      if (::unlink(it->second.path.c_str()) != 0) {
        error_ = "prune_failed";
        return false;
      }
      ::unlink((it->second.path + ".ack").c_str());
      it = segments_.erase(it);
    } else
      ++it;
  }
  return true;
}
bool Store::prune() {
  std::lock_guard<std::mutex> l(mutex_);
  return pruneUnlocked();
}
uint64_t Store::timeline() {
  std::lock_guard<std::mutex> l(mutex_);
  return now_;
}
std::string Store::error() {
  std::lock_guard<std::mutex> l(mutex_);
  return error_;
}
bool Store::healthy() {
  std::lock_guard<std::mutex> l(mutex_);
  return error_.empty();
}
} // namespace yh
