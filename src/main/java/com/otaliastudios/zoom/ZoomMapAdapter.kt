package com.otaliastudios.zoom

import android.view.ViewGroup

abstract class ZoomMapAdapter<VH : ZoomMapViewHolder> {
    private var zoomMap: ZoomMap? = null

    fun bind(view: ZoomMap) {
        zoomMap = view
    }

    abstract fun getChildCount(): Int
    abstract fun createViewHolder(parent: ViewGroup, type: Int): VH
    abstract fun bindViewHolder(viewHolder: VH, position: Int, type: Int)

    open fun getTypeFor(position: Int): Int = 0

    protected fun notifyDataSetChanged() {
        zoomMap?.onAdapterDataSetChanged()
    }
}
