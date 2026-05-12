package com.albirich.RealmsAI.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.SearchResult

class SearchResultAdapter : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    private val results = mutableListOf<SearchResult>()

    fun submitList(newList: List<SearchResult>) {
        results.clear()
        results.addAll(newList)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeBadge: TextView = view.findViewById(R.id.resultTypeBadge)
        val title: TextView = view.findViewById(R.id.resultTitle)
        val content: TextView = view.findViewById(R.id.resultContent)
        val score: TextView = view.findViewById(R.id.resultScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = results[position]
        holder.typeBadge.text = item.type.uppercase()
        holder.title.text = item.title
        holder.content.text = item.content

        // Convert the 0.0 to 1.0 double into a clean percentage (e.g., 85%)
        val percentage = (item.score * 100).toInt()
        holder.score.text = "$percentage% Match"

        // Color code the badge so memories and lore look distinct
        if (item.type == "Memory") {
            holder.typeBadge.setTextColor(android.graphics.Color.parseColor("#64B5F6")) // Light Blue
        } else {
            holder.typeBadge.setTextColor(android.graphics.Color.parseColor("#FFD54F")) // Gold
        }
    }

    override fun getItemCount() = results.size
}