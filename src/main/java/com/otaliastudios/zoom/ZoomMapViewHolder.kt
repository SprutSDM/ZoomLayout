package com.otaliastudios.zoom

import android.view.View

abstract class ZoomMapViewHolder(val view: View) {
    abstract fun getPositionX() : Float
    abstract fun getPositionY() : Float
    abstract fun getPivotX() : Float
    abstract fun getPivotY() : Float

    open fun onVisibilityRateChanged(rate: Float) = Unit
}
