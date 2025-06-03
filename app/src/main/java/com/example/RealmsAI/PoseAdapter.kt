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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pose, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = poses.size

    override fun onBindViewHolder(holder: Holder, pos: Int) {
        val pose = poses[pos]

        holder.label.setText(pose.name)
        holder.label.doAfterTextChanged { editable ->
            pose.name = editable?.toString().orEmpty()
        }
        if (pose.uri != null) {
            holder.img.setImageURI(Uri.parse(pose.uri))
        } else {
            holder.img.setImageResource(R.drawable.placeholder_avatar)
        }
        holder.img.setOnClickListener { onImageClick(pos) }
    }
}
