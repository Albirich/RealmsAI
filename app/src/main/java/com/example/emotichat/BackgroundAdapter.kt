package com.example.emotichat

import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class BackgroundAdapter(
    private val items: List<String>,           // list of URI strings
    private val onSelect: (String)->Unit
): RecyclerView.Adapter<BackgroundAdapter.VH>() {
    inner class VH(val iv: ImageView): RecyclerView.ViewHolder(iv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ImageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(150, 100).apply {
                marginEnd = 16
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        })

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val uri = Uri.parse(items[pos])
        holder.iv.setImageURI(uri)
        holder.iv.setOnClickListener { onSelect(items[pos]) }
    }
}
