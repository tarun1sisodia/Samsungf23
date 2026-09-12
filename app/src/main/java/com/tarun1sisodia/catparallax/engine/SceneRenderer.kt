package com.tarun1sisodia.catparallax.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.tarun1sisodia.catparallax.animation.AnimFrame

/**
 * Composites the layered cat scene onto a Canvas with per-depth parallax and
 * per-role animation transforms.
 *
 * Transform pipeline per layer (identical to tools/cat_design.py, which is how
 * the committed previews were rendered):
 *
 *   canvas.translate(parallaxOffsetPx)
 *   canvas.translate(scene.offsetX, scene.offsetY); canvas.scale(scene.scale)
 *   [role animation: rotate around layer pivot / squash around cat pivot / bounce]
 *   draw layer content in design units
 */
class SceneRenderer(private val scene: CatScene) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var disabledLayers: Set<String> = emptySet()

    /** Max layer shift at depth 1.0 / sensitivity 1.0, in design units. */
    private val maxShiftX = 34f
    private val maxShiftY = 24f

    fun setDisabledLayers(ids: Set<String>) {
        disabledLayers = ids
    }

    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        parallax: ParallaxInput,
        anim: AnimFrame,
        sensitivity: Float
    ) {
        // Sky gradient always fills the surface, so shifts can never reveal a gap.
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.shader = null
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scene.gradientPaint)

        for (layer in scene.layers) {
            if (layer.id in disabledLayers) continue
            if (layer.kind == LayerKind.GRADIENT) continue

            canvas.save()

            // parallax: far layers shift less (depth-scaled)
            val dx = -parallax.roll * layer.depth * maxShiftX * scene.scale * sensitivity
            val dy = parallax.pitch * layer.depth * maxShiftY * scene.scale * sensitivity
            canvas.translate(dx, dy)

            // into design space
            canvas.translate(scene.offsetX, scene.offsetY)
            canvas.scale(scene.scale, scene.scale)

            // role animation, in design units
            when (layer.role) {
                LayerRole.TAIL ->
                    canvas.rotate(anim.tailRotation, layer.pivotX, layer.pivotY)
                LayerRole.EAR_LEFT ->
                    canvas.rotate(anim.earLRotation, layer.pivotX, layer.pivotY)
                LayerRole.EAR_RIGHT ->
                    canvas.rotate(anim.earRRotation, layer.pivotX, layer.pivotY)
                LayerRole.BODY -> {
                    canvas.scale(
                        anim.bodyScaleX, anim.bodyScaleY,
                        scene.catPivotX, scene.catPivotY
                    )
                    canvas.translate(0f, anim.bodyDy)
                }
                else -> Unit
            }

            when (layer.kind) {
                LayerKind.SHAPES -> drawShapes(canvas, layer)
                LayerKind.PATHS -> drawPaths(canvas, layer, anim)
                LayerKind.BITMAP -> drawBitmap(canvas, layer, anim)
                LayerKind.GRADIENT -> Unit
            }

            canvas.restore()
        }
    }

    // ------------------------------------------------------------- shapes --

    private fun drawShapes(canvas: Canvas, layer: ParallaxLayer) {
        val shapes = layer.shapes ?: return
        paint.style = Paint.Style.FILL
        paint.shader = null
        for (s in shapes) {
            paint.color = s.fill
            paint.alpha = (255f * s.alpha).toInt().coerceIn(0, 255)
            when (s.type) {
                ShapeCmd.CIRCLE -> canvas.drawCircle(s.x, s.y, s.r, paint)
                ShapeCmd.ELLIPSE -> {
                    rect.set(s.x - s.rx, s.y - s.ry, s.x + s.rx, s.y + s.ry)
                    canvas.drawOval(rect, paint)
                }
                ShapeCmd.SPARKLE -> {
                    // 4-point star: four quad segments pinched through the center
                    val p = sparklePath(s.x, s.y, s.r)
                    canvas.drawPath(p, paint)
                }
            }
        }
        paint.alpha = 255
    }

    private fun sparklePath(cx: Float, cy: Float, r: Float): Path {
        val p = Path()
        p.moveTo(cx, cy - r)
        p.quadTo(cx, cy, cx + r, cy)
        p.quadTo(cx, cy, cx, cy + r)
        p.quadTo(cx, cy, cx - r, cy)
        p.quadTo(cx, cy, cx, cy - r)
        p.close()
        return p
    }

    // -------------------------------------------------------------- paths --

    private fun drawPaths(canvas: Canvas, layer: ParallaxLayer, anim: AnimFrame) {
        val paths: List<DrawablePath>? = when {
            layer.variants != null ->
                layer.variants[anim.eyeVariant]
                    ?: layer.variants[layer.defaultVariant ?: "open"]
                    ?: layer.variants.values.firstOrNull()
            else -> layer.paths
        }
        if (paths == null) return
        paint.shader = null
        for (dp in paths) {
            val alpha = (255f * dp.alpha).toInt().coerceIn(0, 255)
            if (dp.hasFill) {
                paint.style = Paint.Style.FILL
                paint.color = dp.fill
                paint.alpha = alpha
                canvas.drawPath(dp.path, paint)
            }
            if (dp.hasStroke) {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = dp.stroke
                paint.alpha = alpha
                paint.strokeWidth = dp.strokeWidth
                canvas.drawPath(dp.path, paint)
            }
        }
        paint.alpha = 255
    }

    // ------------------------------------------------------------ bitmaps --

    /**
     * Custom-art layers. Eye cels follow the id convention
     * "eyes_open" / "eyes_closed" / "eyes_wide" — only the cel matching the
     * current variant is drawn (falling back to eyes_open).
     */
    private fun drawBitmap(canvas: Canvas, layer: ParallaxLayer, anim: AnimFrame) {
        val bmp = layer.bitmap ?: return
        val target = layer.bitmapRect ?: return
        if (layer.role == LayerRole.EYES && layer.id.startsWith("eyes_")) {
            val wanted = "eyes_" + anim.eyeVariant
            if (layer.id != wanted && layer.id != "eyes_open") return
            if (layer.id == "eyes_open" && wanted != "eyes_open") {
                // only draw the open fallback if the wanted variant is absent;
                // the wanted cel (if present) draws itself in its own pass
                val hasWanted = scene.layers.any { it.id == wanted }
                if (hasWanted) return
            }
        }
        canvas.drawBitmap(bmp, null, target, bitmapPaint)
    }
}
