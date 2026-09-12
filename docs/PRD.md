# PRD: Cat Parallax Live Wallpaper (Android)

**Target device:** Samsung Galaxy F23, Android 14, One UI 6.1
**Scope:** Home-screen live wallpaper with gyroscope-driven parallax + unlock-triggered animation + user-controlled kill switch
**Status:** Draft v1.0

---

## 1. Problem Statement

The user wants a custom cat character living on their home screen wallpaper that:
1. Shifts/moves in response to phone tilt (gyroscope parallax)
2. Plays a "wake up" animation the moment the phone is unlocked
3. Can be fully stopped on demand — via a home-screen widget button, and via the system's running-apps/task manager — without needing to dig through Settings

Confirmed compatibility: this is built as a standard Android `WallpaperService`, applied to the **home screen** (Android 13+ restricts third-party live wallpapers from the true lock screen surface; Good Lock/Wonderland is unavailable on F-series hardware). The "unlock animation" fires on `ACTION_USER_PRESENT`, i.e. the instant the user lands on the home screen post-unlock — not literally behind the PIN pad.

---

## 2. Goals / Non-Goals

**Goals**
- Smooth, low-jitter tilt parallax using layered sprites
- Idle "alive" behaviors (blink, tail flick) on a randomized timer
- One-shot wake/greet animation on every unlock event
- Instant, reliable stop mechanism from a widget and from the OS task/running-services view
- Low battery/CPU footprint — must not visibly drain battery or show up as a "battery drain" flag in Samsung's device care

**Non-Goals**
- No true lock-screen (pre-unlock) rendering — explicitly out of scope per compatibility findings
- No Good Lock/Wonderland integration — unsupported on F23
- No cloud sync, no multiplayer/social features, no monetization in v1
- No support for other manufacturers' quirks beyond One UI 6.1 in this version

---

## 3. Users & Use Case

Single user (you), single device (F23). No multi-user account system needed. This is a personal-use app; Play Store distribution is optional and not assumed for v1 (can be sideloaded).

---

## 4. Feature List

### 4.1 Core Wallpaper Engine
| Feature | Description | Priority |
|---|---|---|
| Live wallpaper registration | Registers as a selectable home-screen live wallpaper via `WallpaperService` | P0 |
| Layered sprite renderer | Cat split into depth layers (background, body, tail, ears, eyes, foreground) composited by `SceneRenderer` | P0 |
| Gyroscope parallax | Each layer offsets by `depth × sensorDelta`, using `TYPE_GAME_ROTATION_VECTOR` | P0 |
| Sensor smoothing | Low-pass filter to remove jitter; clamp max offset per layer so it never clips off-canvas | P0 |
| Idle animation loop | Randomized blink/tail-flick every 4–12s when phone is still | P1 |
| Unlock wake animation | One-shot animation triggered by `ACTION_USER_PRESENT`, plays once, returns to idle state | P0 |
| Frame throttling | Redraw only on sensor delta above threshold or during active animation; pause entirely when wallpaper not visible (`onVisibilityChanged(false)`) | P0 |

### 4.2 Kill Switch (your explicit ask)
| Feature | Description | Priority |
|---|---|---|
| Home screen widget (App Widget) | A small 1x1 or 2x1 widget with a single toggle button: "Cat: ON/OFF". Tapping it stops the animation/sensor loop immediately without leaving the home screen | P0 |
| Task-manager stoppability | The wallpaper engine must **not** run a separate foreground `Service` with a persistent notification unless the widget is toggled on — this way, swiping the app away in Recent Apps / stopping it in Settings → Apps → Force Stop fully halts everything, with no orphaned background process | P0 |
| Widget state sync | Widget reflects true current state (reads from `PrefsRepository`) even if the app was stopped via task manager rather than the widget | P0 |
| Fallback: revert to static wallpaper on stop | When toggled off, the engine stops sensor listeners and animation loop but keeps the last-rendered frame as a static image, rather than crashing or going blank | P1 |
| Optional: Quick Settings Tile | A `TileService` tile in the notification shade as a second, faster kill switch | P2 (stretch) |

### 4.3 Settings Screen
| Feature | Description | Priority |
|---|---|---|
| Sensitivity slider | Adjusts tilt-to-offset multiplier | P1 |
| Layer opacity/visibility toggles | Turn off individual layers (e.g. disable tail if distracting) | P2 |
| Preview pane | Live preview of the wallpaper inside the settings activity before applying | P1 |
| Battery-saver mode | Reduces frame rate / disables idle animation to save power | P2 |

---

## 5. Architecture

```
CatParallaxWallpaper/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       │   ├── xml/
│       │   │   ├── wallpaper_info.xml          # <wallpaper> meta → points to SettingsActivity
│       │   │   └── cat_widget_info.xml         # AppWidgetProviderInfo (size, update period)
│       │   ├── drawable/                       # cat sprite layers: body, tail, ears, eyes, bg, fg
│       │   ├── raw/                            # cat_idle.json / cat_wake.json (if using Lottie)
│       │   ├── layout/
│       │   │   ├── activity_settings.xml
│       │   │   └── widget_cat_toggle.xml       # widget layout: label + ON/OFF button
│       │   └── values/strings.xml
│       └── java/com/you/catwallpaper/
│           ├── CatWallpaperService.kt          # extends WallpaperService
│           ├── engine/
│           │   ├── CatEngine.kt                # WallpaperService.Engine — owns draw loop, lifecycle
│           │   ├── SensorFusion.kt             # wraps TYPE_GAME_ROTATION_VECTOR + low-pass filter
│           │   ├── ParallaxLayer.kt            # data class: bitmap, depth factor, offset bounds
│           │   └── SceneRenderer.kt            # composites layers per depth onto Canvas
│           ├── animation/
│           │   ├── UnlockAnimator.kt           # reacts to ACTION_USER_PRESENT, plays one-shot anim
│           │   ├── AnimationState.kt           # enum: IDLE, WAKING, BLINK, TAIL_FLICK, PAUSED
│           │   └── AnimationController.kt      # state machine + idle-behavior timer
│           ├── settings/
│           │   ├── SettingsActivity.kt
│           │   └── PrefsRepository.kt          # DataStore: sensitivity, layer toggles, ON/OFF state
│           ├── widget/
│           │   └── CatToggleWidgetProvider.kt  # AppWidgetProvider — sends ON/OFF broadcast to engine
│           └── receiver/
│               ├── UnlockReceiver.kt           # registered/unregistered inside CatEngine lifecycle
│               └── ToggleReceiver.kt           # receives widget tap, updates PrefsRepository + engine state
└── settings.gradle.kts
```

### Key architectural decisions

| Decision | Rationale |
|---|---|
| Canvas 2D rendering, not OpenGL | 5–8 sprite layers don't need a GPU pipeline; Canvas is lighter on battery and easier to debug |
| `TYPE_GAME_ROTATION_VECTOR` over `TYPE_ROTATION_VECTOR` | Gyro-only, no magnetometer drift/jitter |
| No persistent foreground `Service` | This is what makes the task-manager kill switch work — Force Stop or swipe-away fully terminates everything, no orphaned process to worry about |
| Widget writes to shared `PrefsRepository`, engine reads on `onVisibilityChanged` / a lightweight `BroadcastReceiver` | Decouples widget from engine instance — works even if the widget is tapped while the wallpaper isn't currently frontmost |
| `AnimationController` on its own independent timer | Idle behaviors continue regardless of tilt state, layered on top of parallax offset |

---

## 6. Stop / Kill-Switch Flow (detailed)

1. **Via widget:**
   `User taps widget → ToggleReceiver.onReceive() → PrefsRepository.setEnabled(false) → sends local broadcast → CatEngine (if alive) catches broadcast → unregisters SensorFusion listener, cancels AnimationController timer, freezes SceneRenderer on last frame → widget label updates to "Cat: OFF"`

2. **Via task manager / Force Stop:**
   Since there is no foreground service and no persistent notification, swiping the app away in Recent Apps or using Settings → Apps → CatParallaxWallpaper → Force Stop kills the process outright. Android will **revert the live wallpaper to the system default** — so this is a true "hard stop," not just a pause.
   - **Widget toggle = soft stop.** Wallpaper stays applied, but frozen/static, sensors and animations off. Instantly reversible with another tap.
   - **Force Stop / swipe-away = hard stop.** Wallpaper reverts to system default; re-select from Settings → Wallpaper to bring it back.

   This distinction is shown once in the app (a note in Settings) so it's not a surprise.

---

## 7. Permissions Required

| Permission | Why |
|---|---|
| None for sensors | `TYPE_GAME_ROTATION_VECTOR` requires no runtime permission |
| `RECEIVE_BOOT_COMPLETED` (optional, P2) | Only if you want the wallpaper's last ON/OFF widget state restored after a reboot |
| No accessibility permission | Deliberately avoided |

No sensitive/dangerous permissions are needed.

---

## 8. Battery & Performance Requirements

- Redraw only triggered by: (a) sensor delta past a minimum threshold, (b) an active one-shot animation, (c) idle-behavior timer firing. No fixed 60fps redraw loop while static.
- `CatEngine.onVisibilityChanged(false)` must fully unregister the `SensorManager` listener.
- Target: no visible entry in Samsung Device Care's "battery-draining apps" list under normal daily use.

---

## 9. Milestones

| Phase | Deliverable |
|---|---|
| M1 | `CatWallpaperService` + `CatEngine` render a static layered cat, selectable as home wallpaper |
| M2 | `SensorFusion` wired in — tilt-based parallax working, smoothed and clamped |
| M3 | `AnimationController` idle behaviors (blink/tail flick) on timer |
| M4 | `UnlockReceiver` + wake animation on `ACTION_USER_PRESENT` |
| M5 | Widget (`CatToggleWidgetProvider`) with ON/OFF toggle wired to engine via `PrefsRepository` |
| M6 | Settings screen: sensitivity slider + live preview |
| M7 | Battery/perf pass: throttling, visibility-based sensor pause, Device Care check |
| M8 (stretch) | Quick Settings Tile as secondary kill switch |

## 9.5 Future Feature (Phase 2, not in current build): "Angry Bite" on Double-Tap-Lock

**Trigger:** The F23's system gesture "double tap on home screen to lock/turn off screen."

**Desired effect:** On the second tap, the cat reacts as if annoyed/bitten.

**Why deferred:** Samsung's double-tap-to-lock is a system-level shortcut — the screen turns off almost immediately, with no API to delay or intercept the lock so an animation can be seen.

**Planned approach (when built):**
1. Enable `setTouchEventsEnabled(true)` on `CatEngine` and use a `GestureDetector.OnDoubleTapListener` on the wallpaper surface.
2. On detection, set a flag in `PrefsRepository` (`wasInterruptedByLock = true`).
3. On the *next* `ACTION_USER_PRESENT`, `UnlockAnimator` checks this flag: if true, plays an "angry bite" animation instead of the normal wake/greet animation, then clears the flag.

**New assets needed:** one additional animation state (`ANGRY_BITE`) alongside `WAKING` in `AnimationState.kt`, and corresponding sprite frames.

**Status:** Not included in M1–M8. Revisit after the core wallpaper + kill switch (M1–M5) are working and stable.

---

## 10. Decisions Resolved Before Coding

1. **Asset source** — User will provide layered PNG artwork later; v1 ships a code-drawn orange tabby placeholder with a custom-artwork pipeline (`assets/cat/custom/`).
2. **Animation format** — Hand-coded procedural Canvas animations (no Lottie dependency; keeps APK tiny and battery low).
3. **Widget size** — 2×1 with "Cat: ON/OFF" label.
4. **Distribution** — Sideload via GitHub Actions-built release APK.

---

## 11. Explicitly Out of Scope

- True lock-screen (pre-unlock) rendering
- Samsung Good Lock / Wonderland integration
- Accessibility-overlay-based lock screen workaround
- Cross-device (non-F23 / non-One UI 6.1) guarantees

---

## Implementation notes (deviations from the draft, by design)

- **SharedPreferences instead of DataStore** — `PrefsRepository` keeps the same repository façade, but the engine needs synchronous reads inside `onVisibilityChanged`, and avoiding the DataStore/coroutines dependency keeps the APK dependency-free.
- **`ToggleReceiver` folded into `CatToggleWidgetProvider` + engine-side `CatStateReceiver`** — the widget provider already is a broadcast receiver; a second manifest receiver would be redundant.
- **M8 Quick Settings tile is included** in v1.
- **`AnimationState` reserves `ANGRY_BITE`** for Phase 2 (§9.5) so the unlock pipeline can slot it in later.
