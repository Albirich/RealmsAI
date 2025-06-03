package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.PoseSlot
import com.example.RealmsAI.models.Outfit

class OutfitAdapter(
    val outfits: MutableList<Outfit>,
    val onPickPoseImage: (outfitIdx: Int, poseIdx: Int) -> Unit,
    val onAddPose: (outfitIdx: Int) -> Unit,
    val onDeletePose: (outfitIdx: Int, poseIdx: Int) -> Unit,
    val onOutfitNameChanged: (outfitIdx: Int, newName: String) -> Unit
) : RecyclerView.Adapter<OutfitAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val nameEt = v.findViewById<EditText>(R.id.outfitNameEditText)
        val poseRecycler = v.findViewById<RecyclerView>(R.id.poseRecycler)
        val addPoseBtn = v.findViewById<ImageButton>(R.id.addPoseButton)
        val deletePoseBtn = v.findViewById<ImageButton>(R.id.deletePoseButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_outfit, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = outfits.size

    override fun onBindViewHolder(holder: Holder, outfitPos: Int) {
        val outfit = outfits[outfitPos]

        // Outfit name
        holder.nameEt.setText(outfit.name)
        holder.nameEt.doAfterTextChanged { editable ->
            onOutfitNameChanged(outfitPos, editable?.toString().orEmpty())
        }

        // Pose RecyclerView (only needs image click callback)
        holder.poseRecycler.layoutManager =
            LinearLayoutManager(holder.poseRecycler.context, LinearLayoutManager.HORIZONTAL, false)
        val poseAdapter = PoseAdapter(
            poses = outfit.poseSlots,
            onImageClick = { poseIdx -> onPickPoseImage(outfitPos, poseIdx) }
        )
        holder.poseRecycler.adapter = poseAdapter

        // Add pose button
        holder.addPoseBtn.setOnClickListener {
            onAddPose(outfitPos)
            poseAdapter.notifyItemInserted(outfit.poseSlots.size - 1)
        }
        // Delete pose button (removes last pose)
        holder.deletePoseBtn.setOnClickListener {
            if (outfit.poseSlots.isNotEmpty()) {
                onDeletePose(outfitPos, outfit.poseSlots.size - 1)
                poseAdapter.notifyItemRemoved(outfit.poseSlots.size)
            }
        }
    }
}
