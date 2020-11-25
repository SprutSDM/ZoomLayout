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
    open fun onViewDetached(viewHolder: VH) = Unit

    open fun getTypeFor(position: Int): Int = 0

    fun notifyDataSetChanged() {
        zoomMap?.onAdapterDataSetChanged()
    }

    fun notifyDataSetInserted(position: Int, count: Int) {
        zoomMap?.onAdapterDataSetInserted(position = position, count = count)
    }

    fun notifyDataSetRemoved(position: Int, count: Int) {
        zoomMap?.onAdapterDataSetRemoved(position = position, count = count)
    }

    fun notifyDataSetMoved(fromPosition: Int, toPosition: Int) {
        zoomMap?.onAdapterDataSetMoved(fromPosition = fromPosition, toPosition = toPosition)
    }

    fun notifyDataSetChanged(position: Int, count: Int) {
        zoomMap?.onAdapterDataSetChanged(position = position, count = count)
    }
}
