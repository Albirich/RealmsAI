package com.example.RealmsAI

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.PoseSlot

class PoseAdapter(
    private val poses: MutableList<PoseSlot>,
    private val onImageClick: (poseIdx: Int) -> Unit,
    val onDeletePose: (poseIdx: Int) -> Unit,
    val onToggleNsfw: (poseIdx: Int, newValue: Boolean) -> Unit
) : RecyclerView.Adapter<PoseAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val img = v.findViewById<ImageView>(R.id.poseImg)
        val label = v.findViewById<EditText>(R.id.poseLabel)
        val nsfwBadge = v.findViewById<View>(R.id.nsfwBadge)
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
        val uri = pose.uri.orEmpty()
        Glide.with(holder.itemView)
            .load(pose.uri?.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.placeholder_avatar)
            .error(R.drawable.placeholder_avatar)   // or a broken image icon
            .fallback(R.drawable.placeholder_avatar)
            .into(holder.img)

        holder.label.setText(pose.name)

        holder.nsfwBadge.visibility = if (pose.nsfw) View.VISIBLE else View.GONE
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

        holder.img.setOnClickListener { onImageClick(pos) }

        holder.img.setOnLongClickListener {
            val idx = holder.bindingAdapterPosition
            if (idx == RecyclerView.NO_POSITION) return@setOnLongClickListener false

            val ctx = holder.itemView.context
            val isNsfw = poses[idx].nsfw
            val toggleLabel = if (isNsfw) "Unmark NSFW" else "Mark as NSFW"
            val actions = arrayOf(toggleLabel, "Delete")

            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("Pose options")
                .setItems(actions) { _, which ->
                    when (which) {
                        0 -> { // toggle NSFW
                            val newVal = !isNsfw
                            onToggleNsfw(idx, newVal)
                            poses[idx].nsfw = newVal
                            notifyItemChanged(idx) // refresh badge
                        }
                        1 -> { // delete
                            onDeletePose(idx)
                            notifyItemRemoved(idx)
                        }
                    }
                }
                .show()
            true
        }
    }
}
