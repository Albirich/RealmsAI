package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Outfit
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson

class WardrobeActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }

    // pose types
    private val poseKeys = listOf(
        "happy","sad","angry","embarrassed","thinking",
        "flirty","fighting","surprised","frightened","exasperated"
    )

    // We’ll keep one list-of-slots per outfit block
    private val allPoseLists = mutableListOf<MutableList<PoseSlot>>()

    // Which outfit & which pose within it is being picked right now?
    private var currentOutfitIndex = 0
    private var currentPoseIndex   = 0

    // Single global picker
    private lateinit var imagePicker: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wardrobe)

        // 1) register the shared image picker
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                // stick it into the correct PoseSlot
                allPoseLists[currentOutfitIndex][currentPoseIndex].uri = it
                // and notify that block’s RecyclerView to redraw
                val block = (findViewById<LinearLayout>(R.id.outfitsContainer))
                    .getChildAt(currentOutfitIndex)
                val rv    = block.findViewById<RecyclerView>(R.id.poseRecycler)
                rv.adapter?.notifyItemChanged(currentPoseIndex)
            }
        }

        // 2) wire up “Add Outfit”
        findViewById<MaterialButton>(R.id.addOutfitButton)
            .setOnClickListener { addOutfitBlock() }

        // 3) wire up “Save All”
        findViewById<MaterialButton>(R.id.submitWardrobeButton)
            .setOnClickListener { saveAllOutfits() }

        // 4) start with one block already in place
        addOutfitBlock()
    }

    private fun addOutfitBlock() {
        // 1) inflate a new block
        val container = findViewById<LinearLayout>(R.id.outfitsContainer)
        val block = layoutInflater.inflate(R.layout.item_outfit, container, false)
        container.addView(block)

        // 2) create a fresh poseList for this block
        val poseList = poseKeys.map { PoseSlot(key = it, uri = null) }.toMutableList()
        allPoseLists += poseList
        val idx = allPoseLists.size - 1

        // 3) wire up its horizontal RecyclerView
        val rv = block.findViewById<RecyclerView>(R.id.poseRecycler)
        rv.layoutManager = LinearLayoutManager(this, HORIZONTAL, false)
        rv.adapter = PoseAdapter(poseList) { poseIdx ->
            currentOutfitIndex = idx
            currentPoseIndex   = poseIdx
            imagePicker.launch("image/*")
        }
    }

    private fun saveAllOutfits() {
        // Gather them all
        val container = findViewById<LinearLayout>(R.id.outfitsContainer)
        val outfits = mutableListOf<Outfit>()
        for (i in 0 until container.childCount) {
            val block = container.getChildAt(i)
            val nameEt = block.findViewById<EditText>(R.id.outfitNameEditText)
            val name = nameEt.text.toString().trim()

            val poseMap: Map<String, String> = allPoseLists[i]
                .associate { slot ->
                    slot.key to (slot.uri?.toString() ?: "")
                }
            outfits += Outfit(name = name, poseUris = poseMap)
        }

        // return as JSON
        val json = Gson().toJson(outfits)
        Intent().apply {
            putExtra(EXTRA_OUTFITS_JSON, json)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }
}
