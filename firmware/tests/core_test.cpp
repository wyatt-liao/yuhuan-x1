#include "yh/buttons.hpp"
#include "yh/store.hpp"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <vector>

namespace fs = std::filesystem;
using namespace yh;
static void feed(Store &s, unsigned seconds) {
  std::vector<int16_t> frame(480);
  const uint64_t count = uint64_t(seconds) * kRate;
  for (uint64_t offset = 0; offset < count; offset += frame.size()) {
    const size_t n = std::min<uint64_t>(frame.size(), count - offset);
    for (size_t i = 0; i < n; ++i)
      frame[i] = int16_t(
          10000 * std::sin(2 * 3.141592653589793 * 440 * (offset + i) / kRate));
    assert(s.append(frame.data(), n, 1792281600000ull + offset * 1000 / kRate));
  }
}
int main(int argc, char **argv) {
  assert(argc == 2);
  std::string root = argv[1];
  fs::remove_all(root);
  fs::create_directories(root);
  assert(crc32(reinterpret_cast<const uint8_t *>("123456789"), 9) ==
         0xcbf43926);
  uint64_t id = 0;
  assert(parseId("0000000000000001", id) && id == 1);
  assert(!parseId("../file", id));
  assert(!parseId("fffffffffffffffg", id));
  Buttons b;
  assert(b.click(1, 0).action == Action::Start);
  assert(b.click(2, 100).action == Action::Pause);
  assert(b.click(2, 200).action == Action::Resume);
  assert(b.click(1, 300).action == Action::Stop);
  b.click(2, 400);
  b.click(2, 600);
  b.click(2, 800);
  assert(b.tick(1299).action == Action::None);
  auto e = b.tick(1300);
  assert(e.action == Action::Protect && e.hours == 3);
  b.click(2, 1400);
  b.click(1, 1500);
  assert(b.tick(2100).action == Action::None);
  b.click(1, 2200);
  for (int i = 0; i < 30; ++i)
    b.click(2, 3000 + i * 100);
  assert(b.tick(6500).hours == 24);
  VoicePolicy v;
  assert(!v.update(true));
  assert(!v.update(true));
  assert(v.update(true));
  for (int i = 0; i < 99; ++i)
    assert(v.update(false));
  assert(!v.update(false));
  std::puts("PASS button start/stop/pause/resume, click bursts, 24h cap, voice "
            "hysteresis");
  {
    Store s(root + "/ring", 1, 3);
    assert(s.mount());
    feed(s, 2);
    assert(s.checkpoint());
    auto initial = s.list(0, 100);
    assert(initial.size() == 2);
    uint64_t protectedId = initial.back().id;
    assert(s.protectLast(1) > 0);
    feed(s, 5);
    assert(s.checkpoint());
    auto retained = s.list(0, 100);
    bool found = false;
    for (auto &x : retained)
      if (x.id == protectedId) {
        assert(x.protectedAudio);
        found = true;
      }
    assert(found);
    const auto cutoff = s.timeline() - 3 * kRate;
    for (const auto &x : retained)
      assert(x.protectedAudio || x.end() > cutoff);
    assert(retained.size() <= 5);
    auto item = retained.back();
    assert(!s.acknowledge(item.id, item.bytes - 1));
    assert(s.acknowledge(item.id, item.bytes));
    uint8_t p[64];
    assert(s.read(item.id, 0, p, sizeof(p)) == 64);
    assert(validHeader(p));
    assert(s.read(item.id, item.bytes + 1, p, sizeof(p)) < 0);
    fs::copy_file(item.path, root + "/fixture.wav",
                  fs::copy_options::overwrite_existing);
  }
  {
    Store s(root + "/ring", 1, 3);
    assert(s.mount());
    bool synced = false;
    for (auto &x : s.list(0, 100))
      synced |= x.synced;
    assert(synced);
    auto m = s.marks();
    assert(m.size() == 1);
    assert(s.releaseMark(m.front().id));
    assert(s.prune());
    assert(s.marks().empty());
  }
  std::puts("PASS rolling retention, protected overlap, exact-size ACK, read "
            "validation, restart persistence");
  {
    Store s(root + "/manual", 1, 2);
    assert(s.mount());
    auto group = s.startManual();
    assert(group);
    feed(s, 1);
    assert(s.stopManual());
    feed(s, 1);
    assert(s.startManual(group) == group);
    feed(s, 1);
    assert(s.stopManual());
    auto m = s.marks();
    assert(m.size() == 2 && m[0].group == m[1].group);
    assert(m[0].end < m[1].start);
    feed(s, 4);
    assert(s.checkpoint());
    auto files = s.list(0, 100);
    unsigned protectedCount = 0;
    for (auto &x : files)
      protectedCount += x.protectedAudio;
    assert(protectedCount >= 2);
  }
  std::puts("PASS manual pause excludes paused span while background recording "
            "continues");
  {
    Store s(root + "/lease", 1, 1);
    assert(s.mount());
    feed(s, 1);
    assert(s.checkpoint());
    auto item = s.list()[0];
    assert(s.lease(item.id));
    feed(s, 3);
    assert(s.checkpoint());
    Segment x;
    assert(s.lookup(item.id, x));
    s.unlease(item.id);
    assert(s.prune());
    assert(!s.lookup(item.id, x));
  }
  std::puts("PASS in-flight transfer lease prevents rolling deletion");
  {
    fs::create_directories(root + "/recover/audio");
    fs::create_directories(root + "/recover/marks");
    fs::copy_file(
        root + "/fixture.wav",
        root + "/recover/audio/"
               "s0000000000000001_0000000000000000_0000000000000000.prt");
    FILE *f = std::fopen(
        (root + "/recover/audio/"
                "s0000000000000001_0000000000000000_0000000000000000.prt")
            .c_str(),
        "ab");
    assert(f);
    std::fwrite("torn", 1, 4, f);
    std::fclose(f);
    Store s(root + "/recover", 1, 3);
    assert(s.mount());
    auto item = s.list();
    assert(item.size() == 1);
    assert(item[0].bytes > 60);
    assert((item[0].bytes - 60) % 256 == 0);
    assert(item[0].path.find(".wav") != std::string::npos);
  }
  std::puts(
      "PASS power-loss partial-file repair truncates incomplete ADPCM block");
  {
    Store s(root + "/damaged", 1, 1);
    assert(s.mount());
    feed(s, 1);
    assert(s.checkpoint());
    auto mid = s.protectLast(1);
    assert(mid);
  }
  {
    FILE *f = std::fopen(
        (root + "/damaged/marks/m0000000000000001.mark").c_str(), "wb");
    assert(f);
    std::fwrite("bad", 1, 3, f);
    std::fclose(f);
  }
  {
    Store s(root + "/damaged", 1, 1);
    assert(s.mount());
    assert(!s.healthy());
    feed(s, 3);
    assert(s.checkpoint());
    assert(s.list(0, 100).size() >= 4);
  }
  std::puts("PASS corrupt protection metadata disables deletion instead of "
            "losing saved recordings");
  std::puts("ALL CORE TESTS PASSED");
}
