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
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageView

class MapWithPathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val cornerPathEffect = CornerPathEffect(32f)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        pathEffect = cornerPathEffect
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pathDot = Path().apply {
        addCircle(0f, 0f, STROKE_WIDTH / 2f, Path.Direction.CW)
    }

    private val bufferPos = FloatArray(2)
    private val bufferTan = FloatArray(2)

    private var paths = mutableListOf<PathScope>()
    private var pathMeasure = PathMeasure()

    var pathProgress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateDrawable(drawable)
        }

    var mapWidth = 0
    var mapHeight = 0

    fun resetPaths() {
        paths.clear()
        invalidateDrawable(drawable)
    }

    fun addPath(pathPoints: List<PathPoint>, @ColorInt pathColor: Int) {
        val path = Path()
        path.moveTo(pathPoints[0].positionX, pathPoints[0].positionY)
        for (i in 1 until pathPoints.size) {
            path.lineTo(pathPoints[i].positionX, pathPoints[i].positionY)
        }
        pathMeasure.setPath(path, false)
        val pathLength = pathMeasure.length
        paths.add(PathScope(path, pathLength, pathColor))
        invalidateDrawable(drawable)
    }

    @SuppressLint("CanvasSize", "DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        // Bitmap may be recycled by glide, if free memory is ending. So we have to skip a few draw frames until a new
        // bitmap will not loaded
        if ((drawable as? BitmapDrawable)?.bitmap?.isRecycled == true) {
            return
        }
        super.onDraw(canvas)
        canvas.withScale(x = canvas.width.toFloat() / mapWidth, y = canvas.height.toFloat() / mapHeight) {
            paths.forEach { pathScope ->
                linePaint.color = pathScope.color
                dotPaint.color = pathScope.color
                if (pathProgress <= 1f) {
                    val progressEffect = DashPathEffect(
                        floatArrayOf(pathProgress * pathScope.length, 0f, 0f, (1f - pathProgress) * pathScope.length),
                        0f
                    )
                    linePaint.pathEffect = ComposePathEffect(progressEffect, cornerPathEffect)
                }
                canvas.drawPath(pathScope.path, linePaint)

                pathMeasure.setPath(pathScope.path, false)
                pathMeasure.getPosTan(pathProgress * pathScope.length, bufferPos, bufferTan)
                withTranslation(bufferPos[0], bufferPos[1]) {
                    canvas.drawPath(pathDot, dotPaint)
                }
                pathMeasure.getPosTan(pathScope.length, bufferPos, bufferTan)
                withTranslation(bufferPos[0], bufferPos[1]) {
                    canvas.drawPath(pathDot, dotPaint)
                }
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

    /**
     * Wrap the specified [block] in calls to [Canvas.save]/[Canvas.translate]
     * and [Canvas.restoreToCount].
     */
    private inline fun Canvas.withTranslation(
        x: Float = 0.0f,
        y: Float = 0.0f,
        block: Canvas.() -> Unit
    ) {
        val checkpoint = save()
        translate(x, y)
        try {
            block()
        } finally {
            restoreToCount(checkpoint)
        }
    }

    private class PathScope(
        val path: Path,
        val length: Float,
        @ColorInt val color: Int
    )

    data class PathPoint(val positionX: Float, val positionY: Float)

    companion object {
        private const val TAG = "MapWithPathView"

        private const val STROKE_WIDTH = 16f
    }
}
