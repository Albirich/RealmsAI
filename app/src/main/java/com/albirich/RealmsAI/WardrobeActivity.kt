package com.albirich.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.Outfit
import com.albirich.RealmsAI.models.PoseSlot
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson


class WardrobeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }

    private val outfits = mutableListOf<Outfit>()
    private lateinit var outfitAdapter: OutfitAdapter
    private lateinit var imagePicker: ActivityResultLauncher<String>
    private lateinit var outfitRecycler: RecyclerView

    private var currentOutfitIdx = 0
    private var currentPoseIdx = 0
    private var currentVariantIdx: Int? = null

    private lateinit var cropperLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wardrobe)

        outfitRecycler = findViewById(R.id.outfitsRecycler)

        val infoButtonWardrobePage: ImageButton = findViewById(R.id.infoButtonWardrobePage)
        infoButtonWardrobePage.setOnClickListener {
            AlertDialog.Builder(this@WardrobeActivity)
                .setTitle("Outfits")
                .setMessage("The + adds a new pose\n" +
                        "The - deletes the entire outfit\n" +
                        "Click and hold the pose image to get options:\n" +
                        "Delete will delete that pose\n" +
                        "NSFW toggle will set the pose to NSFW and not be sent to SFW AI Messages or shown on the profile.")
                .setPositiveButton("OK", null)
                .show()
        }

        // Find Views
        val outfitRecycler = findViewById<RecyclerView>(R.id.outfitsRecycler)
        val addOutfitBtn = findViewById<MaterialButton>(R.id.addOutfitButton)
        val saveBtn = findViewById<MaterialButton>(R.id.submitWardrobeButton)

        // 1. Load Outfits (edit mode or new)
        // 1. Load Outfits (edit mode or new)
        val outfitsJson = intent.getStringExtra(EXTRA_OUTFITS_JSON)
        if (!outfitsJson.isNullOrBlank()) {
            val loaded = Gson().fromJson(outfitsJson, Array<Outfit>::class.java).toList()

            // 1a. Separate the Main Outfits from the Variants
            val mainOutfits = loaded.filter { it.parentId == null }.toMutableList()
            val flatVariants = loaded.filter { it.parentId != null }

            // 1b. Tuck the variants back inside their parents for the UI
            for (main in mainOutfits) {
                // Ensure the main outfit has a valid name
                if (main.name.isBlank()) main.name = "Unnamed Outfit"

                // Find all variants that belong to this specific main outfit
                main.variants = flatVariants.filter { it.parentId == main.id }.toMutableList()
            }

            outfits.addAll(mainOutfits)
        } else {
            outfits.add(Outfit(name = "Outfit 1", poseSlots = mutableListOf()))
        }

        // 2. Image Picker
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                // Launch cropper activity with this image
                val intent = Intent(this, CropperActivity::class.java).apply {
                    putExtra("EXTRA_IMAGE_URI", it)
                    // Optionally pass in character height or other info
                    putExtra(
                        "CHARACTER_HEIGHT_FEET",
                        intent.getFloatExtra("CHARACTER_HEIGHT_FEET", 6f) // forward it
                    )
                }
                cropperLauncher.launch(intent)
            }
        }

        cropperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val croppedUri = result.data?.getParcelableExtra<Uri>("CROPPED_IMAGE_URI")
                if (croppedUri != null) {
                    // Trigger the details dialog with ALL FOUR dimensions
                    showPoseDetailsDialog(
                        outfitIdx = currentOutfitIdx,
                        poseIdx = currentPoseIdx,
                        variantIdx = currentVariantIdx, // This routes it to the variant if needed!
                        croppedUriString = croppedUri.toString()
                    )
                }
            }
        }

        // 3. Adapter
        outfitAdapter = OutfitAdapter(
            outfits,
            onPickPoseImage = { outfitIdx, poseIdx ->
                currentOutfitIdx = outfitIdx
                currentPoseIdx = poseIdx
                currentVariantIdx = null
                imagePicker.launch("image/*")
            },
            onAddPose = { outfitIdx ->
                // 1. Get current list
                val currentPoses = outfits[outfitIdx].poseSlots

                // 2. CHECK LIMIT: Max 50
                if (currentPoses.size >= 50) {
                    Toast.makeText(this, "Maximum 50 poses per outfit allowed.", Toast.LENGTH_SHORT).show()
                } else {
                    // 3. Add if under limit
                    currentPoses.add(PoseSlot(name = ""))
                    outfitAdapter.notifyItemChanged(outfitIdx)

                    // Debug logs
                    Log.d("WardrobeDebug", "Current outfits:")
                    for ((oIdx, outfit) in outfits.withIndex()) {
                        Log.d("WardrobeDebug", "[$oIdx] Outfit: '${outfit.name}'")
                        for ((pIdx, pose) in outfit.poseSlots.withIndex()) {
                            Log.d("WardrobeDebug", "    [$pIdx] Pose: '${pose.name}' -> '${pose.uri}'")
                        }
                    }
                }
            },
            onDeletePose = { outfitIdx, poseIdx ->
                val poses = outfits[outfitIdx].poseSlots
                if (poseIdx in poses.indices) {
                    poses.removeAt(poseIdx)
                    outfitAdapter.notifyItemChanged(outfitIdx)
                    Log.d("WardrobeDebug", "Current outfits:")
                    for ((outfitIdx, outfit) in outfits.withIndex()) {
                        Log.d("WardrobeDebug", "[$outfitIdx] Outfit: '${outfit.name}'")
                        for ((poseIdx, pose) in outfit.poseSlots.withIndex()) {
                            Log.d("WardrobeDebug", "    [$poseIdx] Pose: '${pose.name}' -> '${pose.uri}'")
                        }
                    }
                }
            },
            onOutfitNameChanged = { outfitIdx, newName ->
                outfits[outfitIdx].name = newName.trim()
                Log.d("WardrobeDebug", "Current outfits:")
                for ((outfitIdx, outfit) in outfits.withIndex()) {
                    Log.d("WardrobeDebug", "[$outfitIdx] Outfit: '${outfit.name}'")
                    for ((poseIdx, pose) in outfit.poseSlots.withIndex()) {
                        Log.d("WardrobeDebug", "    [$poseIdx] Pose: '${pose.name}' -> '${pose.uri}'")
                    }
                }
            },
            onDeleteOutfit = { outfitIdx ->                      // NEW
                val name = outfits.getOrNull(outfitIdx)?.name.orEmpty().ifBlank { "Outfit ${outfitIdx + 1}" }
                AlertDialog.Builder(this)
                    .setTitle("Delete outfit?")
                    .setMessage("This will remove \"$name\" and all its poses.")
                    .setPositiveButton("Delete") { _, _ ->
                        if (outfitIdx in outfits.indices) {
                            outfits.removeAt(outfitIdx)
                            outfitAdapter.notifyItemRemoved(outfitIdx)
                            // Shift positions for items after the removed one
                            outfitAdapter.notifyItemRangeChanged(outfitIdx, outfits.size - outfitIdx)

                            // Keep indices in a safe range
                            if (outfits.isEmpty()) {
                                outfits.add(Outfit(name = "Outfit 1", poseSlots = mutableListOf()))
                                outfitAdapter.notifyItemInserted(0)
                                currentOutfitIdx = 0
                                currentPoseIdx = 0
                            } else {
                                if (currentOutfitIdx >= outfits.size) currentOutfitIdx = outfits.size - 1
                                currentPoseIdx = 0
                                outfitAdapter.notifyItemChanged(currentOutfitIdx)
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onEditPose = { outfitIdx, poseIdx ->
                // Call the dialog WITHOUT an image URI to trigger Edit Mode
                showPoseDetailsDialog(outfitIdx, poseIdx, variantIdx = null)
            },

            onPickVariantPoseImage = { outfitIdx, variantIdx, poseIdx ->
                onPickVariantPoseImage(outfitIdx, variantIdx, poseIdx)
            },

            // NEW: Pass the logic for Variant Pose Editing!
            onEditVariantPose = { outfitIdx, variantIdx, poseIdx ->
                showPoseDetailsDialog(outfitIdx, poseIdx, variantIdx = variantIdx)
            }
        )

        outfitRecycler.layoutManager = LinearLayoutManager(this)
        outfitRecycler.adapter = outfitAdapter

        // 4. Add Outfit Button
        addOutfitBtn.setOnClickListener {
            val currentCount = outfits.size

            // 1. ABSOLUTE LIMIT (10) - Stops everyone
            if (currentCount >= 10) {
                Toast.makeText(this, "You have reached the maximum of 10 outfits.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. CHECK FREE LIMIT (3)
            if (currentCount >= 3) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

                FirebaseFirestore.getInstance().collection("users").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val isPremium = doc.getBoolean("isPremium") ?: false

                        if (isPremium) {
                            // User is Premium and under 10 -> Allow
                            addOutfitRow()
                        } else {
                            // User is Free and hit 3 -> Upsell
                            AlertDialog.Builder(this)
                                .setTitle("Wardrobe Limit Reached")
                                .setMessage("Free users can create up to 3 outfits.\n\nUpgrade to Premium to unlock up to 10 outfits!")
                                .setPositiveButton("Upgrade") { _, _ ->
                                    startActivity(Intent(this, UpgradeActivity::class.java))
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                return@setOnClickListener
            }

            // 3. UNDER 3 -> Allow immediately (No DB call needed)
            addOutfitRow()
        }


        // 5. Save All Outfits
        saveBtn.setOnClickListener {
            val overLimitOutfit = outfits.find { it.description.length > 100 }
                ?: outfits.flatMap { it.variants }.find { it.description.length > 100 }

            if (overLimitOutfit != null) {
                val outfitName = overLimitOutfit.name.ifBlank { "an unnamed outfit/variant" }
                Toast.makeText(this, "Description for $outfitName exceeds 100 characters!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Clean everything (this cleans main outfits AND their nested variants)
            val cleanedOutfits = deduplicateAndCleanOutfits(outfits)

            // THE NEW FLATTENING LOGIC
            val flatDatabaseList = mutableListOf<Outfit>()

            for (main in cleanedOutfits) {
                flatDatabaseList.add(main) // Add the parent

                for (variant in main.variants) {
                    variant.parentId = main.id // Lock in the database link!
                    flatDatabaseList.add(variant) // Add the variant as its own document
                }
            }

            // Save the flattened list! (Gson ignores the nested variants because of @Transient)
            val json = Gson().toJson(flatDatabaseList)

            val resultIntent = Intent().apply {
                putExtra(EXTRA_OUTFITS_JSON, json)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

    }

    private fun showPoseDetailsDialog(
        outfitIdx: Int,
        poseIdx: Int,
        variantIdx: Int? = null, // THE NEW 3RD DIMENSION!
        croppedUriString: String? = null
    ) {
        // 1. Route to the correct list!
        val poseList = if (variantIdx == null) {
            outfits.getOrNull(outfitIdx)?.poseSlots ?: return
        } else {
            outfits.getOrNull(outfitIdx)?.variants?.getOrNull(variantIdx)?.poseSlots ?: return
        }

        if (poseIdx !in poseList.indices) return

        // Because 'currentPose' points to the memory address,
        // modifying it here updates the database perfectly for both!
        val currentPose = poseList[poseIdx]
        val oldDesc = currentPose.description

        // 2. Build the UI programmatically (Unchanged!)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val nameInput = android.widget.EditText(this).apply {
            hint = "Pose Name (e.g., Smirking)"
            setText(currentPose.name)
        }

        val descInput = android.widget.EditText(this).apply {
            hint = "Action Description (e.g., Crosses arms and smirks arrogantly)"
            setText(currentPose.description)
            minLines = 3
            gravity = android.view.Gravity.TOP
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 30
            layoutParams = params
        }

        val nsfwCheckbox = android.widget.CheckBox(this).apply {
            text = "Mark as NSFW (Hides image from public galleries)"
            isChecked = currentPose.nsfw
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 20
            layoutParams = params
        }

        layout.addView(nameInput)
        layout.addView(descInput)
        layout.addView(nsfwCheckbox)

        // 3. Show the Dialog (Unchanged!)
        AlertDialog.Builder(this)
            .setTitle("Pose Details")
            .setMessage("Describe what the character is doing in this image. The AI will use this description to dynamically select this pose during chat.")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameInput.text.toString().trim()
                val newDesc = descInput.text.toString().trim()

                if (croppedUriString != null) {
                    currentPose.uri = croppedUriString
                }

                currentPose.name = if (newName.isNotEmpty()) newName else "Unnamed Pose"
                currentPose.nsfw = nsfwCheckbox.isChecked

                if (oldDesc != newDesc) {
                    currentPose.description = newDesc
                    currentPose.vector = null // Reset math so it regenerates
                }

                // Refreshing the main outfit UI cascades down and refreshes the variant too!
                outfitAdapter.notifyItemChanged(outfitIdx)
            }
            .setNegativeButton("Cancel") { _, _ ->
                if (croppedUriString != null) {
                    Toast.makeText(this, "Pose update cancelled", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun addOutfitRow() {
        outfits.add(Outfit(
            name = "",
            poseSlots = mutableListOf(PoseSlot(name = "Default")),
            isNSFW = false,
            description = ""
        ))
        outfitAdapter.notifyItemInserted(outfits.size - 1)
        // Scroll to the new item
        outfitRecycler.scrollToPosition(outfits.size - 1)
    }

    private fun onPickVariantPoseImage(outfitIdx: Int, variantIdx: Int, poseIdx: Int) {
        currentOutfitIdx = outfitIdx
        currentVariantIdx = variantIdx
        currentPoseIdx = poseIdx
        imagePicker.launch("image/*")
    }


    // Call this right before saving or sending back to CharacterCreationActivity
    // Call this right before saving or sending back to CharacterCreationActivity
    fun deduplicateAndCleanOutfits(outfits: List<Outfit>): List<Outfit> {
        return outfits
            .filter { it.name.trim().isNotEmpty() }
            .groupBy { it.name.trim() }
            .map { (cleanName, group) ->
                val base = group.first() // Grab the original outfit to keep its settings

                Outfit(
                    id = base.id,
                    name = cleanName,
                    isNSFW = base.isNSFW,           // PRESERVE THIS!
                    description = base.description, // PRESERVE THIS!

                    // Clean main poses
                    poseSlots = group.flatMap { it.poseSlots }
                        .filter { it.name.trim().isNotEmpty() }
                        .distinctBy { it.name.trim() }
                        .map { it.copy(name = it.name.trim(), uri = it.uri?.trim()) }
                        .toMutableList(),

                    // PRESERVE & CLEAN VARIANTS!
                    variants = base.variants
                        .filter { it.name.trim().isNotEmpty() }
                        .map { variant ->
                            variant.copy(
                                name = variant.name.trim(),
                                // Clean the variant's poses just like the main ones
                                poseSlots = variant.poseSlots
                                    .filter { it.name.trim().isNotEmpty() }
                                    .distinctBy { it.name.trim() }
                                    .map { it.copy(name = it.name.trim(), uri = it.uri?.trim()) }
                                    .toMutableList()
                            )
                        }.toMutableList()
                )
            }
    }
}

