package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Area
import com.example.RealmsAI.models.LocationSlot
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import java.util.*

class BackgroundGalleryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AREAS_JSON = "EXTRA_AREAS_JSON"
    }

    private val areas = mutableListOf<Area>()
    private lateinit var areaAdapter: AreaAdapter

    private lateinit var imagePicker: ActivityResultLauncher<String>
    private var currentAreaIndex: Int = 0
    private var currentLocationIndex: Int = 0
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_gallery)

        // RecyclerView setup
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
            },
            readonly = false
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = areaAdapter

        // Image Picker
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val area = areas[currentAreaIndex]
                if (area.locations.isNotEmpty() && currentLocationIndex < area.locations.size) {
                    val locationSlot = area.locations[currentLocationIndex]
                    locationSlot.uri = uri.toString()  // Just store the uri string
                    areaAdapter.notifyItemChanged(currentAreaIndex)
                    // Optionally: Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Add Area
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

        loadAreasForPicker { userAreas, _ ->
            areas.clear()
            areas.addAll(userAreas.map { area ->
                area.copy(
                    id = UUID.randomUUID().toString(),
                    creatorId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    locations = area.locations.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList()
                )
            })
            areaAdapter.notifyDataSetChanged()
        }

        // Load Area
        findViewById<MaterialButton>(R.id.loadAreaButton).setOnClickListener {
            loadAreasForPicker { userAreas, defaultAreas ->
                showDualAreaPickerDialog(userAreas, defaultAreas) { pickedArea ->
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

        // Save All
        findViewById<MaterialButton>(R.id.submitGalleryButton).setOnClickListener {
            saveAllAreas()
        }

        // Load passed-in areas
        val areasJson = intent.getStringExtra(EXTRA_AREAS_JSON)
        if (!areasJson.isNullOrBlank()) {
            val loaded = Gson().fromJson(areasJson, Array<Area>::class.java).toList()
            areas.clear()
            areas.addAll(loaded)
            areaAdapter.notifyDataSetChanged()
        } else {
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

    // --- Main Save Logic ---
    private fun saveAllAreas() {
        Log.d("BackgroundGallery", "Starting saveAllAreas. Area count: ${areas.size}")
        showProgressDialog()
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val storage = FirebaseStorage.getInstance()

        val safeName: (String) -> String = { n ->
            n.ifBlank { "unnamed" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(40)
        }

        fun saveAreaWithUploads(area: Area, onComplete: (Boolean) -> Unit) {
            val safeAreaName = safeName(area.name)
            val tasks = mutableListOf<Task<*>>()

            area.locations.forEach { loc ->
                val uriStr = loc.uri
                if (uriStr.isNullOrBlank()) return@forEach
                val fileUri = Uri.parse(uriStr)

                if (fileUri.scheme == "content" || fileUri.scheme == "file") {
                    // Ensure unique file per location using ID
                    val safeLocName = safeName(loc.name) + "_" + (loc.id ?: UUID.randomUUID().toString())
                    val filename = "$safeLocName.jpg"
                    val storageRef = storage.reference.child("backgrounds/$safeAreaName/$filename")
                    val uploadTask = storageRef.putFile(fileUri)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                            storageRef.downloadUrl
                        }
                        .addOnSuccessListener { downloadUrl ->
                            loc.uri = downloadUrl.toString()
                        }
                    tasks.add(uploadTask)
                }
            }

            Tasks.whenAllComplete(tasks).addOnSuccessListener {
                area.creatorId = userId
                val saveDocId = "${userId}_${safeAreaName}"
                db.collection("areas").document(saveDocId).set(area)
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            }.addOnFailureListener {
                onComplete(false)
            }
        }

        fun saveNextArea(index: Int) {
            if (index >= areas.size) {
                Toast.makeText(this, "All areas saved!", Toast.LENGTH_SHORT).show()
                dismissProgressDialog()
                finish()
                return
            }
            saveAreaWithUploads(areas[index]) { success ->
                if (!success) {
                    Toast.makeText(this, "Failed to save area: ${areas[index].name}", Toast.LENGTH_SHORT).show()
                    // Optionally bail out here: return
                }
                saveNextArea(index + 1)
            }
        }

        saveNextArea(0)
    }

    override fun onBackPressed() {
        if (progressDialog != null) {
            Toast.makeText(this, "Please wait—backgrounds are still saving.", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }

    /** Loads both user and default areas from Firestore and returns them as two lists. */
    private fun loadAreasForPicker(callback: (userAreas: List<Area>, defaultAreas: List<Area>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val userAreas = mutableListOf<Area>()
        val defaultAreas = mutableListOf<Area>()

        db.collection("areas")
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener { userSnap ->
                userAreas.addAll(userSnap.documents.mapNotNull { it.toObject(Area::class.java) })
                db.collection("areas")
                    .whereEqualTo("creatorId", "123")
                    .get()
                    .addOnSuccessListener { defaultSnap ->
                        defaultAreas.addAll(defaultSnap.documents.mapNotNull { it.toObject(Area::class.java) })
                        callback(userAreas, defaultAreas)
                    }
                    .addOnFailureListener { callback(userAreas, emptyList()) }
            }
            .addOnFailureListener {
                db.collection("areas")
                    .whereEqualTo("creatorId", "123")
                    .get()
                    .addOnSuccessListener { defaultSnap ->
                        callback(emptyList(), defaultSnap.documents.mapNotNull { it.toObject(Area::class.java) })
                    }
                    .addOnFailureListener { callback(emptyList(), emptyList()) }
            }
    }

    private fun showDualAreaPickerDialog(
        userAreas: List<Area>,
        defaultAreas: List<Area>,
        onPick: (Area) -> Unit
    ) {
        val areaRefs = mutableListOf<Area>()
        val displayNames = mutableListOf<String>()

        if (userAreas.isNotEmpty()) {
            displayNames.add("— My Areas —")
            userAreas.forEach {
                displayNames.add("  ${it.name.ifBlank { "(Unnamed Area)" }}")
                areaRefs.add(it)
            }
        }
        if (defaultAreas.isNotEmpty()) {
            if (displayNames.isNotEmpty()) displayNames.add("— Default Areas —")
            defaultAreas.forEach {
                displayNames.add("  ${it.name.ifBlank { "(Unnamed Area)" }}")
                areaRefs.add(it)
            }
        }

        if (areaRefs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Areas Found")
                .setMessage("No areas were found to load.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Pick Area")
            .setItems(displayNames.filter { !it.startsWith("—") }.toTypedArray()) { _, which ->
                onPick(areaRefs[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProgressDialog() {
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Saving Backgrounds")
            .setMessage("Please don’t close the app. Your backgrounds are being saved and uploaded. This can take a while for large images or slow connections…")
            .setCancelable(false)
            .show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }
}
