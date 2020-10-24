package com.otaliastudios.zoom

import android.view.ViewGroup

abstract class ZoomMapAdapter<VH : ZoomMapViewHolder> {
    private var zoomMap: ZoomMap? = null

    fun bind(view: ZoomMap) {
        zoomMap = view
    }

    abstract fun getChildCount(): Int
    abstract fun createViewHolder(parent: ViewGroup): VH
    abstract fun bindViewHolder(viewHolder: VH, position: Int)

    protected fun notifyDataSetChanged() {
        zoomMap?.onAdapterDataSetChanged()
    }
}
