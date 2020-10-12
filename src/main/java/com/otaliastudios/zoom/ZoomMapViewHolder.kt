package com.otaliastudios.zoom

import android.view.View

abstract class ZoomMapViewHolder(val view: View) {
    abstract fun getVisibilityRate(): Float
    abstract fun getPositionX() : Float
    abstract fun getPositionY() : Float
    abstract fun getXPivot() : Float
    abstract fun getYPivot() : Float
}
