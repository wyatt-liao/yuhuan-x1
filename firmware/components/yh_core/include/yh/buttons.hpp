#pragma once
#include <algorithm>
#include <cstdint>

namespace yh {
enum class ManualState { Idle, Recording, Paused };
enum class Action { None, Start, Stop, Pause, Resume, Protect };
struct ButtonEvent {
  Action action = Action::None;
  unsigned hours = 0;
};
// Feed debounced release events. A second-button click burst is committed after
// 500 ms of silence; it never starts a manual recording or stops background
// audio.
class Buttons {
public:
  ButtonEvent click(unsigned button, uint64_t ms) {
    if (button == 1) {
      clicks_ = 0;
      if (state_ == ManualState::Idle) {
        state_ = ManualState::Recording;
        return {Action::Start, 0};
      }
      state_ = ManualState::Idle;
      return {Action::Stop, 0};
    }
    if (button != 2)
      return {};
    if (state_ == ManualState::Recording) {
      state_ = ManualState::Paused;
      return {Action::Pause, 0};
    }
    if (state_ == ManualState::Paused) {
      state_ = ManualState::Recording;
      return {Action::Resume, 0};
    }
    clicks_ = std::min(clicks_ + 1, 24u);
    last_ = ms;
    return {};
  }
  ButtonEvent tick(uint64_t ms) {
    if (clicks_ && ms >= last_ && ms - last_ >= 500) {
      unsigned n = clicks_;
      clicks_ = 0;
      return {Action::Protect, n};
    }
    return {};
  }
  ManualState state() const { return state_; }
  void reset(ManualState s) {
    state_ = s;
    clicks_ = 0;
  }

private:
  ManualState state_ = ManualState::Idle;
  unsigned clicks_ = 0;
  uint64_t last_ = 0;
};
class VoicePolicy {
public:
  bool update(bool voice) {
    if (voice) {
      attack_ += 30;
      quiet_ = 0;
    } else {
      attack_ = 0;
      quiet_ += 30;
    }
    if (!quad_ && attack_ >= 90)
      quad_ = true;
    if (quad_ && quiet_ >= 3000)
      quad_ = false;
    return quad_;
  }

private:
  unsigned attack_ = 0, quiet_ = 0;
  bool quad_ = false;
};
} // namespace yh
