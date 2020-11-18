package com.otaliastudios.zoom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

class MapWithPathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val cornerPathEffect = CornerPathEffect(32f)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        pathEffect = cornerPathEffect
    }

    private var paths = mutableListOf<PathScope>()
    private var pathLength = 0f
    private var pathMeasure = PathMeasure()

    var pathProgress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updatePath()
        }

    private fun updatePath() {
        if (pathProgress < 1f) {
            val progressEffect = DashPathEffect(
                floatArrayOf(0f, (1f - pathProgress) * pathLength, pathProgress * pathLength, 0f),
                0f
            )
            linePaint.pathEffect = ComposePathEffect(progressEffect, cornerPathEffect)
        }
        invalidateDrawable(drawable)
    }

    var mapWidth = 0
    var mapHeight = 0

    fun resetPaths() {
        paths.clear()
        pathLength = 0f
        updatePath()
    }

    fun addPath(dots: List<Pair<Float, Float>>, @ColorInt pathColor: Int) {
        linePaint.color = pathColor
        val path = Path()
        path.moveTo(dots[0].first, dots[0].second)
        for (i in 1 until dots.size) {
            path.lineTo(dots[i].first, dots[i].second)
        }
        pathMeasure.setPath(path, false)
        pathLength = pathMeasure.length
        paths.add(PathScope(path, pathLength))
        updatePath()
    }

    @SuppressLint("CanvasSize")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withScale(x = canvas.width.toFloat() / mapWidth, y =  canvas.height.toFloat() / mapHeight) {
            paths.forEach { pathScope ->
                canvas.drawPath(pathScope.path, linePaint)
            }
        }
    }

    /**
     * Wrap the specified [block] in calls to [Canvas.save]/[Canvas.scale]
     * and [Canvas.restoreToCount].
     */
    private inline fun Canvas.withScale(
        x: Float = 1.0f,
        y: Float = 1.0f,
        pivotX: Float = 0.0f,
        pivotY: Float = 0.0f,
        block: Canvas.() -> Unit
    ) {
        val checkpoint = save()
        scale(x, y, pivotX, pivotY)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }

    private class PathScope(
        val path: Path,
        val pathLength: Float
    )

    companion object {
        private const val TAG = "MapWithPathView"

        private const val STROKE_WIDTH = 16f
    }
}
