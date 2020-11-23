package com.otaliastudios.zoom

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.otaliastudios.zoom.ZoomApi.ZoomType

class ZoomMap @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @Suppress("MemberVisibilityCanBePrivate") val engine: ZoomEngine = ZoomEngine(context)
) : FrameLayout(context, attrs, defStyleAttr),
    ViewTreeObserver.OnGlobalLayoutListener,
    ZoomApi by engine {

    var adapter: ZoomMapAdapter<ZoomMapViewHolder>? = null
        set(value) {
            field = value
            value?.bind(this)
            onAdapterDataSetChanged()
        }
    private var backgroundWithPath: MapWithPathView? = null
    var mapWidth: Int = 0
        private set
    var mapHeight: Int = 0
        private set
    @ColorInt var defaultPathColor: Int = Color.BLACK
    var pathAnimationDuration: Long = 500

    var virtualWidth: Int = 0
        private set
    var virtualHeight: Int = 0
        private set

    private val visibleViews: MutableList<TypedViewHolder> = mutableListOf()
    private val viewsCache: HashMap<Int, HashSet<ZoomMapViewHolder>> = hashMapOf()

    private var shouldBeUpdatedAfterGlobalLayout: Boolean = true

    private var onOutsideClickListener: OnClickListener? = null

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.ZoomEngine, defStyleAttr, 0)
        val overScrollHorizontal = a.getBoolean(R.styleable.ZoomEngine_overScrollHorizontal, true)
        val overScrollVertical = a.getBoolean(R.styleable.ZoomEngine_overScrollVertical, true)
        val horizontalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_horizontalPanEnabled, true)
        val verticalPanEnabled = a.getBoolean(R.styleable.ZoomEngine_verticalPanEnabled, true)
        val overPinchable = a.getBoolean(R.styleable.ZoomEngine_overPinchable, true)
        val zoomEnabled = a.getBoolean(R.styleable.ZoomEngine_zoomEnabled, true)
        val flingEnabled = a.getBoolean(R.styleable.ZoomEngine_flingEnabled, true)
        val scrollEnabled = a.getBoolean(R.styleable.ZoomEngine_scrollEnabled, true)
        val oneFingerScrollEnabled = a.getBoolean(R.styleable.ZoomEngine_oneFingerScrollEnabled, true)
        val twoFingersScrollEnabled = a.getBoolean(R.styleable.ZoomEngine_twoFingersScrollEnabled, true)
        val threeFingersScrollEnabled = a.getBoolean(R.styleable.ZoomEngine_threeFingersScrollEnabled, true)
        val allowFlingInOverscroll = a.getBoolean(R.styleable.ZoomEngine_allowFlingInOverscroll, true)
        val minZoom = a.getFloat(R.styleable.ZoomEngine_minZoom, ZoomApi.MIN_ZOOM_DEFAULT)
        val maxZoom = a.getFloat(R.styleable.ZoomEngine_maxZoom, ZoomApi.MAX_ZOOM_DEFAULT)
        @ZoomType val minZoomMode = a.getInteger(R.styleable.ZoomEngine_minZoomType, ZoomApi.MIN_ZOOM_DEFAULT_TYPE)
        @ZoomType val maxZoomMode = a.getInteger(R.styleable.ZoomEngine_maxZoomType, ZoomApi.MAX_ZOOM_DEFAULT_TYPE)
        val transformation = a.getInteger(R.styleable.ZoomEngine_transformation, ZoomApi.TRANSFORMATION_CENTER_INSIDE)
        val transformationGravity = a.getInt(R.styleable.ZoomEngine_transformationGravity, ZoomApi.TRANSFORMATION_GRAVITY_AUTO)
        val alignment = a.getInt(R.styleable.ZoomEngine_alignment, ZoomApi.ALIGNMENT_DEFAULT)
        val animationDuration = a.getInt(R.styleable.ZoomEngine_animationDuration, ZoomEngine.DEFAULT_ANIMATION_DURATION.toInt()).toLong()
        val backgroundResId = a.getResourceId(R.styleable.ZoomEngine_background, 0)
        defaultPathColor = a.getColor(R.styleable.ZoomEngine_pathColor, Color.BLACK)
        mapWidth = a.getInt(R.styleable.ZoomEngine_mapWidth, 0)
        mapHeight = a.getInt(R.styleable.ZoomEngine_mapHeight, 0)
        virtualWidth = a.getInt(R.styleable.ZoomEngine_virtualMapWidth, 10_000)
        virtualHeight = a.getInt(R.styleable.ZoomEngine_virtualMapHeight, 10_000)
        pathAnimationDuration = a.getInt(R.styleable.ZoomEngine_pathAnimationDuration, 500).toLong()
        a.recycle()

        engine.setContainer(this)
        engine.addListener(object: ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {}
            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) { onUpdate() }
        })
        setTransformation(transformation, transformationGravity)
        setAlignment(alignment)
        setOverScrollHorizontal(overScrollHorizontal)
        setOverScrollVertical(overScrollVertical)
        setHorizontalPanEnabled(horizontalPanEnabled)
        setVerticalPanEnabled(verticalPanEnabled)
        setOverPinchable(overPinchable)
        setZoomEnabled(zoomEnabled)
        setFlingEnabled(flingEnabled)
        setScrollEnabled(scrollEnabled)
        setOneFingerScrollEnabled(oneFingerScrollEnabled)
        setTwoFingersScrollEnabled(twoFingersScrollEnabled)
        setThreeFingersScrollEnabled(threeFingersScrollEnabled)
        setAllowFlingInOverscroll(allowFlingInOverscroll)
        setAnimationDuration(animationDuration)
        setMinZoom(minZoom, minZoomMode)
        setMaxZoom(maxZoom, maxZoomMode)
        setBackground(backgroundResId)

        setWillNotDraw(false)

        onGlobalLayout()
    }

    //region Internal

    override fun onGlobalLayout() {
        backgroundWithPath?.let {
            engine.setContentSize(it.measuredWidth.toFloat(), it.measuredHeight.toFloat())
            if (it.isLaidOut && shouldBeUpdatedAfterGlobalLayout && engine.zoom != Float.POSITIVE_INFINITY) {
                shouldBeUpdatedAfterGlobalLayout = false
                onUpdate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Measure ourselves as MATCH_PARENT
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            throw RuntimeException("$TAG must be used with fixed dimensions (e.g. match_parent)")
        }
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(widthSize, heightSize)

        // Measure our child as unspecified.
        val spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        measureChildren(spec, spec)
    }

    fun setOnOutsideClickListener(clickListener: OnClickListener) {
        onOutsideClickListener = clickListener
        backgroundWithPath?.setOnClickListener(clickListener)
    }

    fun animatePaths() {
        backgroundWithPath?.let {
            ObjectAnimator.ofFloat(it, "pathProgress", 0f, 1f).apply {
                duration = pathAnimationDuration
                interpolator = LinearInterpolator()
            }.start()
        }
    }

    fun resetPaths() {
        backgroundWithPath?.resetPaths()
    }

    fun addPath(path: List<Pair<Float, Float>>, @ColorInt pathColor: Int = defaultPathColor) {
        backgroundWithPath?.addPath(
            dots = path.map {
                it.first - (virtualWidth - mapWidth) / 2 to it.second - (virtualHeight - mapHeight) / 2
            },
            pathColor = pathColor
        )
    }

    private fun setBackground(@DrawableRes resId: Int) {
        if (backgroundWithPath == null) {
            backgroundWithPath = MapWithPathView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setOnClickListener(onOutsideClickListener)
            }
            addView(backgroundWithPath)
        }
        backgroundWithPath?.apply {
            mapWidth = this@ZoomMap.mapWidth
            mapHeight = this@ZoomMap.mapHeight
            setImageResource(resId)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return engine.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return engine.onTouchEvent(ev) || super.onTouchEvent(ev)
    }

    fun onAdapterDataSetChanged() {
        adapter?.let { adapter ->
            removeAllViews()
            addView(backgroundWithPath)
            visibleViews.forEach {
                putViewHolderIntoCache(it)
            }
            visibleViews.clear()
            for (i in 0 until adapter.getChildCount()) {
                val vhType = adapter.getTypeFor(i)
                val viewHolder = getViewHolder(adapter, vhType)
                adapter.bindViewHolder(viewHolder, i, vhType)
                visibleViews.add(TypedViewHolder(viewHolder, vhType))
                addView(viewHolder.view)
            }
            shouldBeUpdatedAfterGlobalLayout = true
        }
    }

    fun onAdapterDataSetInserted(position: Int, count: Int) {
        adapter?.let { adapter ->
            val viewsForInsert = mutableListOf<TypedViewHolder>()
            for (i in position until position + count) {
                val vhType = adapter.getTypeFor(i)
                val viewHolder = getViewHolder(adapter, vhType)
                adapter.bindViewHolder(viewHolder, i, vhType)
                viewsForInsert.add(TypedViewHolder(viewHolder, vhType))
                addView(viewHolder.view, i + VIEWS_SHIFT)
            }
            visibleViews.addAll(position, viewsForInsert)
            shouldBeUpdatedAfterGlobalLayout = true
        }
    }

    fun onAdapterDataSetRemoved(position: Int, count: Int) {
        adapter?.let { adapter ->
            val vhsForRemove = mutableListOf<TypedViewHolder>()
            for (i in position + count - 1 downTo position) {
                val vh = visibleViews[i]
                vhsForRemove.add(vh)
                putViewHolderIntoCache(vh)
            }
            visibleViews.removeAll(vhsForRemove)
            removeViews(position + VIEWS_SHIFT, count)
            shouldBeUpdatedAfterGlobalLayout = true
        }
    }

    fun onAdapterDataSetMoved(fromPosition: Int, toPosition: Int) {
        adapter?.let { adapter ->
            val viewFirst = getChildAt(fromPosition + VIEWS_SHIFT)
            val viewSecond = getChildAt(toPosition + VIEWS_SHIFT)
            removeViewAt(toPosition + VIEWS_SHIFT)
            addView(viewFirst, toPosition + VIEWS_SHIFT)
            removeViewAt(fromPosition + VIEWS_SHIFT)
            addView(viewSecond, fromPosition + VIEWS_SHIFT)

            val vh = visibleViews[fromPosition]
            visibleViews[fromPosition] = visibleViews[toPosition]
            visibleViews[toPosition] = vh
            shouldBeUpdatedAfterGlobalLayout = true
        }
    }

    fun onAdapterDataSetChanged(position: Int, count: Int) {
        adapter?.let { adapter ->
            for (i in position until position + count) {
                val vh = visibleViews[i]
                adapter.bindViewHolder(vh.viewHolder, i, vh.type)
            }
            shouldBeUpdatedAfterGlobalLayout = true
        }
    }

    /**
     * Returns ViewHolder from cache or create a new instance
     */
    private fun getViewHolder(adapter: ZoomMapAdapter<out ZoomMapViewHolder>, vhType: Int): ZoomMapViewHolder {
        return viewsCache[vhType]?.let { typedViewsCache ->
            typedViewsCache.firstOrNull()?.also {
                typedViewsCache.remove(it)
            } ?: adapter.createViewHolder(this, vhType)
        } ?: adapter.createViewHolder(this, vhType)
    }

    /**
     * Puts ViewHolder into cache.
     */
    private fun putViewHolderIntoCache(viewHolder: TypedViewHolder) {
        if (viewHolder.type !in viewsCache) {
            viewsCache[viewHolder.type] = hashSetOf()
        }
        viewsCache[viewHolder.type]?.add(viewHolder.viewHolder)
    }

    private fun onUpdate() {
        // Update background
        backgroundWithPath?.let {
            it.pivotX = 0f
            it.pivotY = 0f
            it.translationX = engine.scaledPanX
            it.translationY = engine.scaledPanY
            it.scaleX = engine.realZoom
            it.scaleY = engine.realZoom
        }
        val zoomWidthPercent = (engine.zoom - getMinZoom()) / (getMaxZoom() - getMinZoom())
        val zoomDepthWidth = (MAX_DEPTH_RATE_AT_ZOOM - MIN_DEPTH_RATE_AT_ZOOM)
        val zoomDepthRate = zoomWidthPercent * zoomDepthWidth + MIN_DEPTH_RATE_AT_ZOOM

        visibleViews.forEach {
            val vh = it.viewHolder
            val realXPosition = vh.getPositionX() - (virtualWidth - mapWidth) / 2f
            val realYPosition = vh.getPositionY() - (virtualHeight - mapHeight) / 2f
            val newTranslationX = scaledPanX - vh.getPivotX() +
                    realXPosition / mapWidth * engine.contentWidth * engine.realZoom
            val newTranslationY = scaledPanY - vh.getPivotY() +
                    realYPosition / mapHeight * engine.contentHeight * engine.realZoom
            vh.view.apply {
                translationX = newTranslationX
                translationY = newTranslationY
            }
            vh.onVisibilityRateChanged(zoomDepthRate)
        }
        if ((isHorizontalScrollBarEnabled || isVerticalScrollBarEnabled) && !awakenScrollBars()) {
            invalidate()
        }
    }

    override fun computeHorizontalScrollOffset(): Int = engine.computeHorizontalScrollOffset()

    override fun computeHorizontalScrollRange(): Int = engine.computeHorizontalScrollRange()

    override fun computeVerticalScrollOffset(): Int = engine.computeVerticalScrollOffset()

    override fun computeVerticalScrollRange(): Int = engine.computeVerticalScrollRange()

    private class TypedViewHolder(val viewHolder: ZoomMapViewHolder, val type: Int)

    companion object {
        private val TAG = ZoomMap::class.java.simpleName
        private const val MAX_DEPTH_RATE_AT_ZOOM = 1.2f
        private const val MIN_DEPTH_RATE_AT_ZOOM = 0.4f

        // Count of views that placed before recycle views
        private const val VIEWS_SHIFT = 1
    }
}
