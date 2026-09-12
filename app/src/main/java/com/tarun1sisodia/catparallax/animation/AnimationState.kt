package com.tarun1sisodia.catparallax.animation

/**
 * Animation states for the cat.
 *
 * IDLE        — sitting still, eyes open, tail at rest
 * WAKING      — one-shot wake/greet sequence (unlock animation)
 * BLINK       — short blink
 * TAIL_FLICK  — tail swish
 * PAUSED      — soft stop (kill switch): everything frozen
 *
 * ANGRY_BITE is reserved for Phase 2 (PRD §9.5: the double-tap-lock "you
 * locked her mid-nap" payback on next unlock). It is intentionally not
 * reachable yet.
 */
enum class AnimationState {
    IDLE,
    WAKING,
    BLINK,
    TAIL_FLICK,
    PAUSED,
    ANGRY_BITE
}
