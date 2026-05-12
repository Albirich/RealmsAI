package com.albirich.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.Outfit

class VariantAdapter(
    val variants: MutableList<Outfit>,
    val onPickPoseImage: (variantIdx: Int, poseIdx: Int) -> Unit,
    val onAddPose: (variantIdx: Int) -> Unit,
    val onDeletePose: (variantIdx: Int, poseIdx: Int) -> Unit,
    val onEditPose: (variantIdx: Int, poseIdx: Int) -> Unit,
    val onDeleteVariant: (variantIdx: Int) -> Unit
) : RecyclerView.Adapter<VariantAdapter.VariantHolder>() {

    inner class VariantHolder(v: View) : RecyclerView.ViewHolder(v) {
        val nameEt = v.findViewById<EditText>(R.id.variantNameEditText)
        val descriptionEt = v.findViewById<EditText>(R.id.variantDescriptionEditText)
        val poseRecycler = v.findViewById<RecyclerView>(R.id.poseRecycler)
        val addPoseBtn = v.findViewById<ImageButton>(R.id.addPoseButton)

        // Note: Using your XML ID 'deletePoseButton' as the 'Delete Variant' button
        val deleteVariantBtn = v.findViewById<ImageButton>(R.id.deletePoseButton)
        val nsfwBtn = v.findViewById<Button>(R.id.nsfwVariantButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VariantHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_variant, parent, false)
        return VariantHolder(v)
    }

    override fun onBindViewHolder(holder: VariantHolder, position: Int) {
        val variant = variants[position]
        // bindingAdapterPosition is safer than 'position' for dynamic lists!
        val variantPos = holder.bindingAdapterPosition

        // 1. Text Watchers (Saves data when user types)
        holder.nameEt.setText(variant.name)
        holder.nameEt.doAfterTextChanged { editable ->
            variant.name = editable?.toString() ?: ""
        }

        holder.descriptionEt.setText(variant.description)
        holder.descriptionEt.doAfterTextChanged { editable ->
            variant.description = editable?.toString() ?: ""
        }

        // 2. Setup the Horizontal PoseRecycler
        holder.poseRecycler.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)

        // Brilliantly reusing your existing PoseAdapter!
        val poseAdapter = PoseAdapter(
            poses = variant.poseSlots,
            onImageClick = { poseIdx -> onPickPoseImage(variantPos, poseIdx) },
            onDeletePose = { poseIdx -> onDeletePose(variantPos, poseIdx) },
            onEditPose = { poseIdx -> onEditPose(variantPos, poseIdx) }
        )
        holder.poseRecycler.adapter = poseAdapter

        // 3. Add Pose / Delete Variant Buttons
        holder.addPoseBtn.setOnClickListener {
            // Fetch the live position exactly when clicked!
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onAddPose(currentPos)
                val newPoseIndex = variants[currentPos].poseSlots.size - 1
                poseAdapter.notifyItemInserted(newPoseIndex)
                holder.poseRecycler.smoothScrollToPosition(newPoseIndex)
            }
        }

        holder.deleteVariantBtn.setOnClickListener {
            onDeleteVariant(variantPos)
        }

        // 4. NSFW Button Logic
        updateNsfwUi(holder, variant.isNSFW)
        holder.nsfwBtn.setOnClickListener {
            variant.isNSFW = !variant.isNSFW
            updateNsfwUi(holder, variant.isNSFW)
        }
    }



    override fun getItemCount(): Int = variants.size

    // Standard UI toggle for NSFW
    private fun updateNsfwUi(holder: VariantHolder, isNsfw: Boolean) {
        if (isNsfw) {
            // Reusing the red border from your OutfitAdapter
            holder.itemView.setBackgroundResource(R.drawable.red_border)
            holder.nsfwBtn.text = "NSFW"
        } else {
            holder.itemView.setBackgroundResource(R.drawable.rounded_border)
            holder.nsfwBtn.text = "SFW"
        }
    }
}