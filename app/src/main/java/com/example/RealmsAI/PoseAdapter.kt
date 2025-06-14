package com.example.RealmsAI

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.PoseSlot

class PoseAdapter(
    private val poses: MutableList<PoseSlot>,
    private val onImageClick: (poseIdx: Int) -> Unit
) : RecyclerView.Adapter<PoseAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val img = v.findViewById<ImageView>(R.id.poseImg)
        val label = v.findViewById<EditText>(R.id.poseLabel)
        var watcher: android.text.TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pose, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = poses.size

    override fun onBindViewHolder(holder: Holder, pos: Int) {
        // Remove old watcher to avoid duplicates
        holder.watcher?.let { holder.label.removeTextChangedListener(it) }

        val pose = poses[pos]
        holder.label.setText(pose.name)

        // New watcher just for this holder
        val newWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(editable: android.text.Editable?) {
                val adapterPos = holder.adapterPosition
                if (adapterPos != RecyclerView.NO_POSITION) {
                    poses[adapterPos].name = editable?.toString().orEmpty()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        holder.label.addTextChangedListener(newWatcher)
        holder.watcher = newWatcher

        // Image logic
        if (pose.uri != null) {
            holder.img.setImageURI(Uri.parse(pose.uri))
        } else {
            holder.img.setImageResource(R.drawable.placeholder_avatar)
        }
        holder.img.setOnClickListener { onImageClick(pos) }
    }
}
