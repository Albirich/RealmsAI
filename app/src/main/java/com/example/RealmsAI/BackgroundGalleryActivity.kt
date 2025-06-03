package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Area
import com.example.RealmsAI.models.LocationSlot
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.util.*

class BackgroundGalleryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AREAS_JSON = "EXTRA_AREAS_JSON"
    }

    private val areas = mutableListOf<Area>()
    private lateinit var areaAdapter: AreaAdapter

    // Global image picker
    private lateinit var imagePicker: ActivityResultLauncher<String>
    private var currentAreaIndex: Int = 0
    private var currentLocationIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_gallery)

        // Init RecyclerView
        val recycler = findViewById<RecyclerView>(R.id.areaRecycler)
        areaAdapter = AreaAdapter(
            areas = areas,
            onPickImage = { areaIdx, locIdx ->
                currentAreaIndex = areaIdx
                currentLocationIndex = locIdx
                imagePicker.launch("image/*")
            },
            onDeleteLocation = { areaIdx, locIdx ->
                if (areas[areaIdx].locations.size > 1) {
                    areas[areaIdx].locations.removeAt(locIdx)
                    areaAdapter.notifyItemChanged(areaIdx)
                }
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = areaAdapter

        // Set up image picker
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val area = areas[currentAreaIndex]
                if (area.locations.isNotEmpty() && currentLocationIndex < area.locations.size) {
                    area.locations[currentLocationIndex].uri = it.toString()
                    areaAdapter.notifyItemChanged(currentAreaIndex)
                }
            }
        }

        // Add Area button
        findViewById<MaterialButton>(R.id.addAreaButton).setOnClickListener {
            areas.add(
                Area(
                    id = UUID.randomUUID().toString(),
                    creatorId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    name = "",
                    locations = mutableListOf(LocationSlot())
                )
            )
            areaAdapter.notifyItemInserted(areas.size - 1)
        }

        // Load Area button
        findViewById<MaterialButton>(R.id.loadAreaButton).setOnClickListener {
            loadAreasForPicker { loadedAreas ->
                showAreaPickerDialog(loadedAreas) { pickedArea ->
                    // Clone and add
                    val newArea = pickedArea.copy(
                        id = UUID.randomUUID().toString(),
                        creatorId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                        locations = pickedArea.locations.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList()
                    )
                    areas.add(newArea)
                    areaAdapter.notifyItemInserted(areas.size - 1)
                }
            }
        }

        // Save All Backgrounds
        findViewById<MaterialButton>(R.id.submitGalleryButton).setOnClickListener {
            saveAllAreas()
        }

        // Load any passed-in areas
        val areasJson = intent.getStringExtra(EXTRA_AREAS_JSON)
        if (!areasJson.isNullOrBlank()) {
            val loaded = Gson().fromJson(areasJson, Array<Area>::class.java).toList()
            areas.clear()
            areas.addAll(loaded)
            areaAdapter.notifyDataSetChanged()
        } else {
            // Start with one blank area
            areas.add(
                Area(
                    id = UUID.randomUUID().toString(),
                    creatorId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    name = "",
                    locations = mutableListOf(LocationSlot())
                )
            )
            areaAdapter.notifyItemInserted(areas.size - 1)
        }
    }

    private fun saveAllAreas() {
        // You’d likely upload to Firestore/Storage here!
        val json = Gson().toJson(areas)
        Intent().apply {
            putExtra(EXTRA_AREAS_JSON, json)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    // Loads both default and user areas
    private fun loadAreasForPicker(callback: (List<Area>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val allAreas = mutableListOf<Area>()

        db.collection("areas")
            .whereEqualTo("creatorId", "default")
            .get()
            .addOnSuccessListener { defaultSnap ->
                allAreas.addAll(defaultSnap.toObjects(Area::class.java))
                db.collection("areas")
                    .whereEqualTo("creatorId", userId)
                    .get()
                    .addOnSuccessListener { userSnap ->
                        allAreas.addAll(userSnap.toObjects(Area::class.java))
                        callback(allAreas)
                    }
                    .addOnFailureListener { callback(allAreas) }
            }
            .addOnFailureListener { callback(allAreas) }
    }

    // Simple picker dialog for now (just shows names)
    private fun showAreaPickerDialog(areas: List<Area>, onPick: (Area) -> Unit) {
        if (areas.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Areas Found")
                .setMessage("No areas were found to load.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = areas.map { it.name.ifBlank { "(Unnamed Area)" } }
        AlertDialog.Builder(this)
            .setTitle("Pick Area")
            .setItems(names.toTypedArray()) { _, which ->
                onPick(areas[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
