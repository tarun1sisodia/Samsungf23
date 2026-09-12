package com.tarun1sisodia.catparallax.engine

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Scene model classes, parsed from assets/cat/default_cat.json (or the user's
 * custom PNG set in assets/cat/custom/ — see that folder's README).
 *
 * All geometry lives in a normalized "design space" (1000 x 1600 by default);
 * SceneRenderer maps design units onto the physical surface.
 */

enum class LayerKind { GRADIENT, SHAPES, PATHS, BITMAP }

/** Which animation channel drives this layer (see AnimationController). */
enum class LayerRole { NONE, BODY, TAIL, EAR_LEFT, EAR_RIGHT, EYES }

/** One filled/stroked path in design coordinates. */
class DrawablePath(
    val path: Path,
    val fill: Int,
    val hasFill: Boolean,
    val stroke: Int,
    val hasStroke: Boolean,
    val strokeWidth: Float,
    val alpha: Float
)

/** Shape primitives (stars, bokeh, glow pools). */
class ShapeCmd(
    val type: Int,              // 0 = circle, 1 = ellipse, 2 = sparkle
    val x: Float,
    val y: Float,
    val r: Float,
    val rx: Float,
    val ry: Float,
    val fill: Int,
    val alpha: Float
) {
    companion object {
        const val CIRCLE = 0
        const val ELLIPSE = 1
        const val SPARKLE = 2
    }
}

/**
 * A single parallax layer. Depth 0 = far (barely moves), 1 = near (moves most).
 * [pivotX]/[pivotY] is the design-space point the layer rotates around.
 */
class ParallaxLayer(
    val id: String,
    val kind: LayerKind,
    val depth: Float,
    val role: LayerRole,
    val toggleable: Boolean,
    val pivotX: Float,
    val pivotY: Float,
    val paths: List<DrawablePath>?,
    val variants: Map<String, List<DrawablePath>>?,
    val defaultVariant: String?,
    val shapes: List<ShapeCmd>?,
    val bitmap: Bitmap?,
    val bitmapRect: RectF?
)

/** Normalized tilt input in [-1, 1], produced by SensorFusion. */
class ParallaxInput(val pitch: Float, val roll: Float) {
    companion object {
        val ZERO = ParallaxInput(0f, 0f)
    }
}

/** A loaded scene, pre-fitted to a specific surface size. */
class CatScene(
    val designWidth: Float,
    val designHeight: Float,
    val catPivotX: Float,
    val catPivotY: Float,
    val layers: List<ParallaxLayer>,
    val toggleableLayerIds: List<String>,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val skyTopColor: Int,
    val gradientPaint: Paint
) {
    /** Design units -> surface pixels. */
    fun toScreenX(x: Float): Float = x * scale + offsetX
    fun toScreenY(y: Float): Float = y * scale + offsetY
}
