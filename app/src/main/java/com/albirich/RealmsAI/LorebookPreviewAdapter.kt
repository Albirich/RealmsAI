package com.albirich.RealmsAI.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.Lorebook

class LorebookPreviewAdapter(
    private val context: Context,
    private var lorebooks: List<Lorebook>,
    private val onClick: (Lorebook) -> Unit,
    private val onLongClick: (Lorebook) -> Unit
) : RecyclerView.Adapter<LorebookPreviewAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coverImage: ImageView = view.findViewById(R.id.loreCoverImage)
        val titleText: TextView = view.findViewById(R.id.loreTitle)
        val countText: TextView = view.findViewById(R.id.loreEntryCount)
        val descText: TextView = view.findViewById(R.id.loreDescription)
        val scrollView: ScrollView = view.findViewById(R.id.descScrollView)

        init {
            view.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) onClick(lorebooks[adapterPosition])
            }
            view.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) onLongClick(lorebooks[adapterPosition])
                true
            }
            scrollView.setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_lorebook_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = lorebooks[position]

        holder.titleText.text = book.title.ifBlank { "Unnamed Lorebook" }
        holder.descText.text = book.description

        val entryWord = if (book.entries.size == 1) "Entry" else "Entries"
        holder.countText.text = "${book.entries.size} $entryWord"

        if (!book.coverUri.isNullOrBlank()) {
            Glide.with(context).load(book.coverUri).centerCrop().into(holder.coverImage)
        } else {
            holder.coverImage.setImageResource(R.drawable.placeholder_avatar)
        }
    }

    // Inside LorebookPreviewAdapter:
    fun updateList(newList: List<Lorebook>) {
        this.lorebooks = newList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = lorebooks.size
}
