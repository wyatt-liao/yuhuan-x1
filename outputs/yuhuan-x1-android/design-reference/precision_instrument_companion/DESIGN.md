---
name: Precision Instrument Companion
colors:
  surface: '#f6fafa'
  surface-dim: '#d6dbda'
  surface-bright: '#f6fafa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f0f4f4'
  surface-container: '#eaefee'
  surface-container-high: '#e5e9e8'
  surface-container-highest: '#dfe3e3'
  on-surface: '#181c1c'
  on-surface-variant: '#3f4947'
  inverse-surface: '#2c3131'
  inverse-on-surface: '#edf2f1'
  outline: '#6f7977'
  outline-variant: '#bec9c6'
  surface-tint: '#1c6963'
  primary: '#004a45'
  on-primary: '#ffffff'
  primary-container: '#12635d'
  on-primary-container: '#95dcd4'
  inverse-primary: '#8cd3cb'
  secondary: '#516161'
  on-secondary: '#ffffff'
  secondary-container: '#d4e6e5'
  on-secondary-container: '#576867'
  tertiary: '#88000b'
  on-tertiary: '#ffffff'
  tertiary-container: '#b20d17'
  on-tertiary-container: '#ffc0b9'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#a8f0e7'
  primary-fixed-dim: '#8cd3cb'
  on-primary-fixed: '#00201d'
  on-primary-fixed-variant: '#00504b'
  secondary-fixed: '#d4e6e5'
  secondary-fixed-dim: '#b8cac9'
  on-secondary-fixed: '#0e1e1e'
  on-secondary-fixed-variant: '#3a4a49'
  tertiary-fixed: '#ffdad6'
  tertiary-fixed-dim: '#ffb4ac'
  on-tertiary-fixed: '#410002'
  on-tertiary-fixed-variant: '#93000d'
  background: '#f6fafa'
  on-background: '#181c1c'
  surface-variant: '#dfe3e3'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Manrope
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  body-lg:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.01em
  body-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.015em
  body-sm:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-lg:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.03em
  label-sm:
    fontFamily: Roboto Flex
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system defines the digital companion interface for ultra-slim hardware audio recording tools. The aesthetic direction bridges tactile physical engineering with modern digital utility, establishing a tone that is quiet, precise, paper-like, and reassuringly professional.

Rather than aggressive tech minimalism or playful consumer gadget aesthetics, the design adopts a refined instrument style. It draws from tactile precision measuring tools, high-grade stationery, and classic studio hardware, while adhering strictly to Material 3 foundation standards for Android.

### Key Principles
- **Quiet Precision**: Restrained interactions without unnecessary micro-animations; crisp 1px structural framing; high-fidelity audio waveform and timeline visualizations.
- **Physical Warmth**: Tactile background shades resembling warm paper and frosted metallic finishes, preventing eye fatigue during lengthy transcription and playback sessions.
- **Instrument Reliability**: Crucial hardware statuses (BLE sync, battery levels, memory gauge, dual-mic gain) are legible at a glance with zero visual ambiguity.

## Colors

The palette balances warm paper surfaces with an authoritative deep verdigris teal, supported by deliberate alerts reserved exclusively for live acoustic capture and destructive actions.

### Palette Architecture
- **Primary (`#12635D`)**: Deep Verdigris. Anchors active tab states, hardware link confirmations, primary action buttons, scrubber handles, and active audio visualization playheads.
- **Secondary (`#E0F2F1`)**: Soft Mint Wash. Used for selected card fills, active track segment highlights, and pill container backgrounds.
- **Tertiary / Critical (`#E53935`)**: Warm Vermilion. Strictly isolated to recording state indicators (pulsing recording beacon, hardware recording trigger) and destructive actions. Never used for decorative accents.
- **Neutral Foreground (`#1F2424`)**: Deep Charcoal. Primary readability token for high-contrast titles, transcriptions, and numerical displays.
- **Secondary Text (`#596161`)**: Mid-tone slate for timestamps, speaker attributions, and secondary metadata.
- **Tertiary Text (`#8C9494`)**: Muted slate for inactive labels, tick-mark values, and disabled hints.
- **Canvas Base (`#F9F9F6`)**: Warm Paper Cream. Outer layout scaffolding, system status bar background, and screen container background.
- **Card / Surface (`#FFFFFF`)**: Pure White. Floating modules, audio segment transcripts, and interactive cards.
- **Hairline Border (`#E6E8E6`)**: Crisp, calibrated 1px structural dividing line for cards, dividers, and acoustic tick rulers.

## Typography

The type scale combines **Manrope** for geometric, calm, balanced structural anchors with **Roboto Flex** for mechanical clarity, data density, and transcription legibility.

### Rules of Application
- **Headers & Timers (Manrope)**: Used for major screen titles, elapsed record timers (`00:14:32`), device models, and AI summary highlights. Employs tight tracking to maintain an engineered, compact visual footprint.
- **Transcripts & Telemetry (Roboto Flex)**: Used for body transcription text, hardware diagnostic numbers, battery percentage values, and contextual metadata. High legibility at small sizes prevents visual noise across lengthy audio scripts.
- **Monospace Digits**: All audio timestamps, millisecond scrubbers, and percentage stats must render with tabular figures (`tnum`) to eliminate layout jitter during live updates.

## Layout & Spacing

The layout is optimized around the modern Android mobile baseline standard of **412dp viewport width**, utilizing an adaptable 4-column fluid mobile grid.

### Layout Mechanics
- **Grid Configuration**: 4 columns with 16dp (`1rem`) gutters and 16dp outer screen margins.
- **Vertical Rhythm**: Built on an 8dp structural grid, using 4dp half-steps (`space-xs`) strictly for micro-alignments such as badge padding, icon-to-label spacing, and waveform bar separation.
- **Touch Target Integrity**: Every interactive item maintains a mandatory minimum hit box of **48dp × 48dp**, regardless of its rendered icon or indicator size.
- **System Offsets**: Strictly accounts for Android status bar heights (24dp to 48dp depending on camera punch-hole cutouts) and bottom system gesture navigation handles (minimum 16dp bottom protection buffer above the navigation bar).

## Elevation & Depth

To express the physical thinness and instrument quality of the paired hardware, this system avoids heavy drop shadows and dramatic skeuomorphic bevels. Instead, depth is conveyed through subtle tonal layering and crisp architectural hairpins.

### Depth Hierarchy
- **Level 0 (Base Canvas)**: `#F9F9F6`. The baseline background for main application screens.
- **Level 1 (Surface Cards & Lists)**: `#FFFFFF` surface enclosed by a crisp `1px solid #E6E8E6` border. No drop shadow. Depth is communicated strictly through tonal contrast between the warm canvas and the pure white container.
- **Level 2 (Active States & Popovers)**: `#FFFFFF` surface paired with an ambient shadow: `0px 4px 16px rgba(18, 99, 93, 0.06)`, bounded by a `1px solid #E6E8E6` stroke.
- **Level 3 (Floating Persistent Controls & Scrubber Thumbs)**: `#12635D` or `#FFFFFF` paired with `0px 6px 20px rgba(31, 36, 36, 0.08)`.

## Shapes

The shape hierarchy establishes a balanced corner radius profile (`roundedness: 2`), reflecting the smooth CNC-milled corners of aluminum card-type devices.

### Curvature Tokens
- **Small Corners (`rounded`, 8px / 0.5rem)**: Standard buttons, text input fields, audio tag chips, and playback control toggles.
- **Large Corners (`rounded-lg`, 16px / 1rem)**: Recording cards, transcription transcript boxes, device status dashboards, and modal sheets.
- **Full Pill (`rounded-full`, 9999px)**: Status badges (e.g., "Connected", "Transcribing"), scrubber handle heads, bottom floating pill selectors, and primary recording controls.

## Components

### 1. Navigation Framework
- **Top App Bar**: 56dp height. Houses the device connection status pill on the left (BLE signal icon + battery icon + percentage in `#596161`), a centered screen title in Manrope 16px SemiBold, and action icons (search/filter) on the right.
- **Bottom Navigation Bar**: Fixed 4-destination Android bottom bar with icons and text labels:
  - *Today* (`今天`)
  - *Recordings* (`录音`)
  - *Assistant* (`助手`)
  - *Device* (`设备`)
  Container height: 80dp including system inset. Active state uses `#12635D` tint with a light pill indicator (`#E0F2F1`). Inactive states remain `#596161`.

### 2. Audio Waveform & Timeline Scrubber
- **Waveform Canvas**: Vertical metering bars with a 2px stroke width and 2px inter-bar spacing.
- **Color Logic**: Played segments render in primary Deep Teal (`#12635D`); unplayed upcoming audio renders in muted gray (`#E6E8E6`); voice-activity-detected (VAD) segments feature heightened amplitude contrast.
- **Timecode Ruler**: 1px vertical tick lines placed every 1 second, with labeled text markers every 5 seconds set in Roboto Flex 11px Monospace (`#8C9494`).

### 3. Recording Beacon & Floating Actions
- **Live State Indicator**: A pulsing 8dp dot in Vermilion (`#E53935`) accompanied by a subtle 16dp radial aura (`rgba(229, 57, 53, 0.15)`).
- **Primary Hardware Trigger Button**: 56dp circular FAB styled with `#12635D` surface and white iconography, elevated at Level 3. When active recording is engaged, the FAB transitions to `#E53935` with a square stop glyph.

### 4. Interactive Cards
- **Structure**: `#FFFFFF` background, 16px corner radius, 1px `#E6E8E6` perimeter border. Padding: 16dp.
- **Recording List Item**: Contains recording title, relative timestamp, duration tag (`04:35`), file size (`4.2 MB`), and an AI summary snippet truncated to two lines.

### 5. Chips & Metadata Badges
- **Status Badges**: Pill-shaped (`rounded-full`), height: 24dp, horizontal padding: 8dp. Text set in Label-Small (11px).
- **Filter Chips**: Height: 32dp, 8dp border radius, `#FFFFFF` background with 1px border. When selected, transitions to `#E0F2F1` background with `#12635D` text and border.

### 6. Form Inputs & Switches
- **Input Fields**: Outlined style with 1px `#E6E8E6` stroke, 8dp corner radius, 48dp minimum height. Active focus shifts the border to `#12635D` at 1.5px without visual layout shift.
- **Hardware Feature Switch**: Material 3 switch specification, thumb colored `#12635D` when active, resting on an `#E0F2F1` track.