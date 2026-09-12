package com.tarun1sisodia.catparallax.animation

/**
 * Reacts to ACTION_USER_PRESENT (the instant the user lands on the home
 * screen post-unlock) and plays the one-shot wake/greet animation.
 *
 * If the engine isn't live at that moment (e.g. the broadcast lands before
 * onVisibilityChanged(true)), the greeting is queued and delivered as soon as
 * the wallpaper becomes visible.
 *
 * Phase 2 hook (PRD §9.5): when the "angry bite" feature is built, this is
 * where a wasInterruptedByLock flag check will choose ANGRY_BITE over WAKING.
 */
class UnlockAnimator(
    private val controller: AnimationController,
    private val isActive: () -> Boolean
) {

    private var pendingUnlock = false

    fun onUnlock() {
        if (isActive()) {
            controller.playWake()
        } else {
            pendingUnlock = true
        }
    }

    /** Call from onVisibilityChanged(true). */
    fun deliverPending() {
        if (pendingUnlock) {
            pendingUnlock = false
            if (isActive()) controller.playWake()
        }
    }

    fun clear() {
        pendingUnlock = false
    }
}
