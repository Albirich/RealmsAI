package com.albirich.RealmsAI.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.Lorebook

class MiniLorebookAdapter(
    private var lorebooks: List<Lorebook>,
    private val onDelete: (Lorebook) -> Unit
) : RecyclerView.Adapter<MiniLorebookAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.miniLoreCover)
        val title: TextView = view.findViewById(R.id.miniLoreTitle)
        val deleteBtn: ImageView = view.findViewById(R.id.miniLoreDelete)

        init {
            deleteBtn.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onDelete(lorebooks[adapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mini_lorebook, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = lorebooks[position]
        holder.title.text = book.title

        if (!book.coverUri.isNullOrBlank()) {
            Glide.with(holder.itemView.context).load(book.coverUri).centerCrop().into(holder.cover)
        } else {
            holder.cover.setImageResource(R.drawable.placeholder_avatar)
        }
    }

    override fun getItemCount(): Int = lorebooks.size

    fun updateList(newList: List<Lorebook>) {
        lorebooks = newList
        notifyDataSetChanged()
    }
}