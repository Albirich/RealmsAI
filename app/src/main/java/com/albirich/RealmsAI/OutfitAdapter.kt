package com.albirich.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.PoseSlot
import com.albirich.RealmsAI.models.Outfit

class OutfitAdapter(
    val outfits: MutableList<Outfit>,
    val onPickPoseImage: (outfitIdx: Int, poseIdx: Int) -> Unit,
    val onAddPose: (outfitIdx: Int) -> Unit,
    val onDeletePose: (outfitIdx: Int, poseIdx: Int) -> Unit,
    val onOutfitNameChanged: (outfitIdx: Int, newName: String) -> Unit,
    val onDeleteOutfit: (outfitIdx: Int) -> Unit,
    val onEditPose: (outfitIdx: Int, poseIdx: Int) -> Unit,
    val onPickVariantPoseImage: (outfitIdx: Int, variantIdx: Int, poseIdx: Int) -> Unit,
    val onEditVariantPose: (outfitIdx: Int, variantIdx: Int, poseIdx: Int) -> Unit
) : RecyclerView.Adapter<OutfitAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val nameEt = v.findViewById<EditText>(R.id.outfitNameEditText)
        val descriptionEt = v.findViewById<EditText>(R.id.outfitDescriptionEditText)
        val poseRecycler = v.findViewById<RecyclerView>(R.id.poseRecycler)
        val addPoseBtn = v.findViewById<ImageButton>(R.id.addPoseButton)
        val deletePoseBtn = v.findViewById<ImageButton>(R.id.deletePoseButton)
        val nsfwBtn = v.findViewById<Button>(R.id.nsfwOutfitButton)
        val variantRecycler = v.findViewById<RecyclerView>(R.id.variantRecycler)
        val variantAddBtn = v.findViewById<Button>(R.id.variantAddBtn)
        val transformationToggle = v.findViewById<android.widget.TextView>(R.id.transformationToggle)
        val transformationContainer = v.findViewById<android.widget.LinearLayout>(R.id.transformationContainer)
        val heightOverrideEt = v.findViewById<EditText>(R.id.heightOverrideEt)
        val weightOverrideEt = v.findViewById<EditText>(R.id.weightOverrideEt)
        val eyeColorOverrideEt = v.findViewById<EditText>(R.id.eyeColorOverrideEt)
        val hairColorOverrideEt = v.findViewById<EditText>(R.id.hairColorOverrideEt)
        val physicalDescOverrideEt = v.findViewById<EditText>(R.id.physicalDescOverrideEt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_outfit, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = outfits.size

    override fun onBindViewHolder(holder: Holder, outfitPos: Int) {
        val outfit = outfits[outfitPos]

        // 1. Outfit name and description
        holder.nameEt.setText(outfit.name)
        holder.nameEt.doAfterTextChanged { editable ->
            onOutfitNameChanged(outfitPos, editable?.toString().orEmpty())
        }

        holder.descriptionEt.setText(outfit.description)
        holder.descriptionEt.doAfterTextChanged { editable ->
            outfit.description = editable?.toString().orEmpty() // Save it directly to the object!
        }

        // --- TRANSFORMATION OVERRIDES UI ---

        // 1. Pre-fill the boxes if data exists
        holder.heightOverrideEt.setText(outfit.heightOverride)
        holder.weightOverrideEt.setText(outfit.weightOverride)
        holder.eyeColorOverrideEt.setText(outfit.eyeColorOverride)
        holder.hairColorOverrideEt.setText(outfit.hairColorOverride)
        holder.physicalDescOverrideEt.setText(outfit.physicalDescOverride)

        // 2. Check if the container should start open or closed
        val hasOverrides = !outfit.heightOverride.isNullOrBlank() ||
                !outfit.weightOverride.isNullOrBlank() ||
                !outfit.eyeColorOverride.isNullOrBlank() ||
                !outfit.hairColorOverride.isNullOrBlank() ||
                !outfit.physicalDescOverride.isNullOrBlank()

        if (hasOverrides) {
            holder.transformationContainer.visibility = View.VISIBLE
            holder.transformationToggle.text = "- Hide Transformation Overrides"
        } else {
            holder.transformationContainer.visibility = View.GONE
            holder.transformationToggle.text = "+ Add Transformation Overrides"
        }

        // 3. The Click Listener to toggle visibility
        holder.transformationToggle.setOnClickListener {
            val isVisible = holder.transformationContainer.visibility == View.VISIBLE
            if (isVisible) {
                holder.transformationContainer.visibility = View.GONE
                holder.transformationToggle.text = "+ Add Transformation Overrides"
            } else {
                holder.transformationContainer.visibility = View.VISIBLE
                holder.transformationToggle.text = "- Hide Transformation Overrides"
            }
        }

        // 4. Save the inputs directly to the outfit object as they type
        holder.heightOverrideEt.doAfterTextChanged { outfit.heightOverride = it?.toString()?.takeIf { s -> s.isNotBlank() } }
        holder.weightOverrideEt.doAfterTextChanged { outfit.weightOverride = it?.toString()?.takeIf { s -> s.isNotBlank() } }
        holder.eyeColorOverrideEt.doAfterTextChanged { outfit.eyeColorOverride = it?.toString()?.takeIf { s -> s.isNotBlank() } }
        holder.hairColorOverrideEt.doAfterTextChanged { outfit.hairColorOverride = it?.toString()?.takeIf { s -> s.isNotBlank() } }
        holder.physicalDescOverrideEt.doAfterTextChanged { outfit.physicalDescOverride = it?.toString()?.takeIf { s -> s.isNotBlank() } }

        // 2. Pose RecyclerView
        holder.poseRecycler.layoutManager =
            LinearLayoutManager(holder.poseRecycler.context, LinearLayoutManager.HORIZONTAL, false)
        val poseAdapter = PoseAdapter(
            poses = outfit.poseSlots,
            onImageClick = { poseIdx -> onPickPoseImage(outfitPos, poseIdx) },
            onDeletePose = { poseIdx ->
                onDeletePose(outfitPos, poseIdx)
                (holder.poseRecycler.adapter as? PoseAdapter)?.notifyItemRemoved(poseIdx)
            },
            onEditPose = { poseIdx -> onEditPose(outfitPos, poseIdx) }
        )
        holder.poseRecycler.adapter = poseAdapter

        // 3. Add/Delete Buttons
        holder.addPoseBtn.setOnClickListener {
            onAddPose(outfitPos)
            val newPoseIndex = outfit.poseSlots.size - 1
            (holder.poseRecycler.adapter as? PoseAdapter)?.notifyItemInserted(outfit.poseSlots.size - 1)
            holder.poseRecycler.smoothScrollToPosition(newPoseIndex)
        }
        holder.deletePoseBtn.setOnClickListener {
            onDeleteOutfit(outfitPos)
        }

        // 4. Set Initial NSFW UI State
        updateNsfwUi(holder, outfit.isNSFW)

        // 5. Toggle NSFW State on Click
        holder.nsfwBtn.setOnClickListener {
            outfit.isNSFW = !outfit.isNSFW // Flip the boolean
            updateNsfwUi(holder, outfit.isNSFW) // Update the visuals
        }

        holder.variantRecycler.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.VERTICAL, false)

        // We will plug in the VariantAdapter here in the next step!
        val variantAdapter = VariantAdapter(
            variants = outfit.variants,

            // Image Click
            onPickPoseImage = { variantIdx, poseIdx ->
                // Now it uses the callback passed from the constructor!
                onPickVariantPoseImage(outfitPos, variantIdx, poseIdx)
            },

            // Add Pose
            onAddPose = { variantIdx ->
                outfit.variants[variantIdx].poseSlots.add(PoseSlot(name = "New Pose"))
            },

            // Delete Pose
            onDeletePose = { variantIdx, poseIdx ->
                outfit.variants[variantIdx].poseSlots.removeAt(poseIdx)
            },

            // Edit Pose Details
            onEditPose = { variantIdx, poseIdx ->
                // Now it uses the callback passed from the constructor!
                onEditVariantPose(outfitPos, variantIdx, poseIdx)
            },

            // Delete the Variant itself
            onDeleteVariant = { variantIdx ->
                outfit.variants.removeAt(variantIdx)
                holder.variantRecycler.adapter?.notifyItemRemoved(variantIdx)
            }
        )


        holder.variantRecycler.adapter = variantAdapter

        // 2. Add Variant Button Logic
        holder.variantAddBtn.setOnClickListener {
            // --- THE NEW LIMIT CHECK ---
            if (outfit.variants.size >= 5) { // Set your cap here (e.g., 5 or 10)
                Toast.makeText(holder.itemView.context, "Maximum 5 variants per outfit allowed.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener // Stop the code right here!
            }

            // Add a new empty outfit to this outfit's variants list
            outfit.variants.add(
                Outfit(
                    name = "Variant ${outfit.variants.size + 1}",
                    // THE FIX: Give it a default pose immediately!
                    poseSlots = mutableListOf(PoseSlot(name = "Default"))
                )
            )
            // Tell the variant adapter to update
            holder.variantRecycler.adapter?.notifyItemInserted(outfit.variants.size - 1)
        }
    }

    // Helper function to easily swap the background and text
    private fun updateNsfwUi(holder: Holder, isNsfw: Boolean) {
        if (isNsfw) {
            holder.itemView.setBackgroundResource(R.drawable.nsfwoutline)
            holder.nsfwBtn.text = "NSFW"
        } else {
            holder.itemView.setBackgroundResource(R.drawable.outline)
            holder.nsfwBtn.text = "SFW"
        }
    }
}