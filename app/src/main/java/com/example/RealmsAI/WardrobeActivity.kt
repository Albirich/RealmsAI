package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Outfit
import com.example.RealmsAI.models.PoseSlot
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson


class WardrobeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }

    private val outfits = mutableListOf<Outfit>()
    private lateinit var outfitAdapter: OutfitAdapter
    private lateinit var imagePicker: ActivityResultLauncher<String>

    private var currentOutfitIdx = 0
    private var currentPoseIdx = 0
    private lateinit var cropperLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wardrobe)

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
        val outfitsJson = intent.getStringExtra(EXTRA_OUTFITS_JSON)
        if (!outfitsJson.isNullOrBlank()) {
            val loaded = Gson().fromJson(outfitsJson, Array<Outfit>::class.java)
            // Ensure every loaded outfit has a non-blank name
            loaded.forEachIndexed { idx, outfit ->
                if (outfit.name.isNullOrBlank()) {
                    outfit.name = "Outfit ${idx + 1}"
                    // Optionally: you could use something more descriptive or unique if needed
                }
            }

            outfits.addAll(loaded)
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
                    val poseList = outfits.getOrNull(currentOutfitIdx)?.poseSlots ?: return@registerForActivityResult
                    if (currentPoseIdx in poseList.indices) {
                        poseList[currentPoseIdx].uri = croppedUri.toString()
                        outfitAdapter.notifyItemChanged(currentOutfitIdx)
                    }
                }
                Log.d("WardrobeDebug", "Current outfits:")
                for ((outfitIdx, outfit) in outfits.withIndex()) {
                    Log.d("WardrobeDebug", "[$outfitIdx] Outfit: '${outfit.name}'")
                    for ((poseIdx, pose) in outfit.poseSlots.withIndex()) {
                        Log.d("WardrobeDebug", "    [$poseIdx] Pose: '${pose.name}' -> '${pose.uri}'")
                    }
                }
            }
        }

        // 3. Adapter
        outfitAdapter = OutfitAdapter(
            outfits,
            onPickPoseImage = { outfitIdx, poseIdx ->
                currentOutfitIdx = outfitIdx
                currentPoseIdx = poseIdx
                imagePicker.launch("image/*")
            },
            onAddPose = { outfitIdx ->
                outfits[outfitIdx].poseSlots.add(PoseSlot(name = ""))
                outfitAdapter.notifyItemChanged(outfitIdx)
                Log.d("WardrobeDebug", "Current outfits:")
                for ((outfitIdx, outfit) in outfits.withIndex()) {
                    Log.d("WardrobeDebug", "[$outfitIdx] Outfit: '${outfit.name}'")
                    for ((poseIdx, pose) in outfit.poseSlots.withIndex()) {
                        Log.d("WardrobeDebug", "    [$poseIdx] Pose: '${pose.name}' -> '${pose.uri}'")
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
            }
        )


        outfitRecycler.layoutManager = LinearLayoutManager(this)
        outfitRecycler.adapter = outfitAdapter

        // 4. Add Outfit Button
        addOutfitBtn.setOnClickListener {
            outfits.add(Outfit(name = "", poseSlots = mutableListOf()))
            outfitAdapter.notifyItemInserted(outfits.size - 1)

        }
        // 5. Save All Outfits
        saveBtn.setOnClickListener {
            val cleanedOutfits = deduplicateAndCleanOutfits(outfits)
            val json = Gson().toJson(cleanedOutfits)
            val resultIntent = Intent().apply {
                putExtra(EXTRA_OUTFITS_JSON, json)
            }
            Log.d("WardrobeDebug", "Current outfits:")
            for ((outfitIdx, outfit) in outfits.withIndex()) {
                Log.d("WardrobeDebug", "[$outfitIdx] Outfit: '${outfit.name}'")
                for ((poseIdx, pose) in outfit.poseSlots.withIndex()) {
                    Log.d("WardrobeDebug", "    [$poseIdx] Pose: '${pose.name}' -> '${pose.uri}'")
                }
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
    // Call this right before saving or sending back to CharacterCreationActivity
    fun deduplicateAndCleanOutfits(outfits: List<Outfit>): List<Outfit> {
        return outfits
            .filter { it.name.trim().isNotEmpty() }
            .groupBy { it.name.trim() }
            .map { (cleanName, group) ->
                Outfit(
                    name = cleanName,
                    poseSlots = group.flatMap { it.poseSlots }
                        .filter { it.name.trim().isNotEmpty() }
                        .distinctBy { it.name.trim() } // Unique pose names per outfit
                        .map { it.copy(name = it.name.trim(), uri = it.uri?.trim()) }
                        .toMutableList()
                )
            }
    }

}
