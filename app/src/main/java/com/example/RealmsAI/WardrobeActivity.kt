package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wardrobe)

        // Find Views
        val outfitRecycler = findViewById<RecyclerView>(R.id.outfitsRecycler)
        val addOutfitBtn = findViewById<MaterialButton>(R.id.addOutfitButton)
        val saveBtn = findViewById<MaterialButton>(R.id.submitWardrobeButton)

        // 1. Load Outfits (edit mode or new)
        val outfitsJson = intent.getStringExtra(EXTRA_OUTFITS_JSON)
        if (!outfitsJson.isNullOrBlank()) {
            val loaded = Gson().fromJson(outfitsJson, Array<Outfit>::class.java)
            outfits.addAll(loaded)
        } else {
            outfits.add(Outfit(name = "", poseSlots = mutableListOf()))
        }

        // 2. Image Picker
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val poseList = outfits.getOrNull(currentOutfitIdx)?.poseSlots ?: return@let
                if (currentPoseIdx in poseList.indices) {
                    poseList[currentPoseIdx].uri = it.toString()
                    outfitAdapter.notifyItemChanged(currentOutfitIdx)
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
            },
            onDeletePose = { outfitIdx, poseIdx ->
                val poses = outfits[outfitIdx].poseSlots
                if (poseIdx in poses.indices) {
                    poses.removeAt(poseIdx)
                    outfitAdapter.notifyItemChanged(outfitIdx)
                }
            },
            onOutfitNameChanged = { outfitIdx, newName ->
                outfits[outfitIdx].name = newName
            }
            // <-- REMOVE onPoseNameChanged, it's not used in OutfitAdapter now!
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
            val json = Gson().toJson(outfits)
            val resultIntent = Intent().apply {
                putExtra(EXTRA_OUTFITS_JSON, json)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
