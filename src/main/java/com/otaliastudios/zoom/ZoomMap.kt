package com.otaliastudios.zoom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.AttrRes
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
            onAdapterDataSetChanged()
        }
    private var backgroundImage: ImageView? = null
    var mapWidth: Int = 0
        private set
    var mapHeight: Int = 0
        private set

    private var visibleViews: List<TypedViewHolder> = emptyList()
    private val viewsCache: HashMap<Int, HashSet<ZoomMapViewHolder>> = hashMapOf()

    private var wasUpdatedAtFirstGlobalLayout: Boolean = false

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
        mapWidth = a.getInt(R.styleable.ZoomEngine_mapWidth, 0)
        mapHeight = a.getInt(R.styleable.ZoomEngine_mapHeight, 0)
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
        backgroundImage?.let {
            engine.setContentSize(it.measuredWidth.toFloat(), it.measuredHeight.toFloat())
            if (it.isLaidOut && !wasUpdatedAtFirstGlobalLayout && engine.zoom != Float.POSITIVE_INFINITY) {
                wasUpdatedAtFirstGlobalLayout = true
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
        backgroundImage?.setOnClickListener(clickListener)
    }

    private fun setBackground(@DrawableRes resId: Int) {
        if (backgroundImage == null) {
            backgroundImage = ImageView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setOnClickListener(onOutsideClickListener)
            }
            addView(backgroundImage)
        }
        backgroundImage?.setImageResource(resId)
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
            addView(backgroundImage)
            visibleViews.forEach {
                if (it.type !in viewsCache) {
                    viewsCache[it.type] = hashSetOf()
                }
                viewsCache[it.type]?.add(it.viewHolder)
            }
            val newVisibleViews = mutableListOf<TypedViewHolder>()
            for (i in 0 until adapter.getChildCount()) {
                val vhType = adapter.getTypeFor(i)
                val viewHolder = viewsCache[vhType]?.let { typedViewsCache ->
                    typedViewsCache.firstOrNull()?.also {
                        typedViewsCache.remove(it)
                    } ?: adapter.createViewHolder(this, vhType)
                } ?: adapter.createViewHolder(this, vhType)
                adapter.bindViewHolder(viewHolder, i, vhType)
                newVisibleViews.add(TypedViewHolder(viewHolder, vhType))
                addView(viewHolder.view)
            }
            visibleViews = newVisibleViews
        }
    }

    private fun onUpdate() {
        // Update background
        backgroundImage?.let {
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
            val newTranslationX = scaledPanX - vh.getPivotX() +
                    vh.getPositionX() / mapWidth * engine.contentWidth * engine.realZoom
            val newTranslationY = scaledPanY - vh.getPivotY() +
                    vh.getPositionY() / mapHeight * engine.contentHeight * engine.realZoom
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
    }
}
