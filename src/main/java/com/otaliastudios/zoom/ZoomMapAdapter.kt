package com.otaliastudios.zoom

import android.view.ViewGroup

abstract class ZoomMapAdapter<VH : ZoomMapViewHolder> {
    abstract fun getChildCount(): Int
    abstract fun createViewHolder(parent: ViewGroup): VH
    abstract fun bindViewHolder(viewHolder: VH, position: Int)
}
