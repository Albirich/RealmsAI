package com.albirich.RealmsAI

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.Area
import com.albirich.RealmsAI.models.LocationSlot
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class AreaCreationActivity : AppCompatActivity() {

    private lateinit var currentArea: Area
    private lateinit var locationAdapter: LocationAdapter // Reusing your existing Location adapter!

    private lateinit var nameEt: EditText
    private lateinit var descEt: EditText
    private lateinit var publicInfoEt: EditText
    private lateinit var nsfwSwitch: Switch
    private lateinit var privateSwitch: Switch
    private lateinit var locationRecycler: RecyclerView
    private var progressDialog: AlertDialog? = null

    private lateinit var imagePicker: ActivityResultLauncher<String>
    private var editingLocation: LocationSlot? = null
    private var dialogImageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_area)

        nameEt = findViewById(R.id.areaNameEt)
        descEt = findViewById(R.id.areaDescEt)
        publicInfoEt = findViewById(R.id.areaPublicInfoEt)
        nsfwSwitch = findViewById(R.id.areaNsfwSwitch)
        privateSwitch = findViewById(R.id.areaPrivateSwitch)
        locationRecycler = findViewById(R.id.locationCreateRecycler)

        // 1. Initialize an empty Area (or load an existing one if passed via Intent for editing)
        val editAreaId = intent.getStringExtra("AREA_EDIT_ID")
        if (editAreaId != null) {
            loadAreaForEditing(editAreaId)
        } else {
            currentArea = Area(creatorId = FirebaseAuth.getInstance().currentUser?.uid ?: "")
            setupRecyclerView()
        }

        // 2. Setup Image Picker (Exactly like your old one)
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                if (editingLocation != null && dialogImageView != null) {
                    editingLocation!!.uri = it.toString()
                    Glide.with(this).load(it).into(dialogImageView!!)
                }
            }
        }

        // 3. Add Location Button
        findViewById<Button>(R.id.btnAddLocation).setOnClickListener {
            if (currentArea.locations.size >= 20) {
                Toast.makeText(this, "Max 20 locations allowed.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showLocationDialog(null) // Pass null to create a new one
        }

        // 4. Save Button
        findViewById<Button>(R.id.btnSaveArea).setOnClickListener {
            saveArea()
        }

        // --- BACK BUTTON INTERCEPTOR ---
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@AreaCreationActivity)
                    .setTitle("Discard Progress?")
                    .setMessage("If you go back now, all of your unsaved Location details will be lost. Are you sure?")
                    .setPositiveButton("Discard") { _, _ ->
                        // Actually close the activity and go back
                        finish()
                    }
                    .setNegativeButton("Keep Editing", null) // Do nothing, just dismiss the popup
                    .show()
            }
        })
    }

    private fun setupRecyclerView() {
        // You can just reuse your LocationAdapter from before!
        locationAdapter = LocationAdapter(
            locations = currentArea.locations,
            onPickImage = { loc -> showLocationDialog(loc) },
            readonly = false
        )
        locationRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        locationRecycler.adapter = locationAdapter
    }

    private fun showLocationDialog(existingLocation: LocationSlot?) {
        val isNew = (existingLocation == null)
        val tempLocation = existingLocation?.copy() ?: LocationSlot(name = "", description = "")

        // Track for image picker
        editingLocation = tempLocation

        val dialogView = layoutInflater.inflate(R.layout.dialog_location_editor, null)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val imgView = dialogView.findViewById<ImageView>(R.id.dialogLocationImage)
        val nameInput = dialogView.findViewById<EditText>(R.id.dialogNameInput)
        val descInput = dialogView.findViewById<EditText>(R.id.dialogDescInput)
        val saveBtn = dialogView.findViewById<Button>(R.id.dialogSaveBtn)
        val deleteBtn = dialogView.findViewById<Button>(R.id.dialogDeleteBtn)
        val cancelBtn = dialogView.findViewById<Button>(R.id.dialogCancelBtn)

        // Store ref for image picker update
        dialogImageView = imgView

        // Populate Fields
        titleView.text = if (isNew) "Add Location" else "Edit Location"
        nameInput.setText(tempLocation.name)
        descInput.setText(tempLocation.description)

        if (!tempLocation.uri.isNullOrBlank()) {
            Glide.with(this).load(tempLocation.uri).into(imgView)
        }

        // Bind Actions
        imgView.setOnClickListener {
            imagePicker.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        if (isNew) deleteBtn.visibility = View.GONE

        deleteBtn.setOnClickListener {
            if (!isNew) {
                val index = currentArea.locations.indexOfFirst { it.id == existingLocation?.id }
                if (index != -1) {
                    currentArea.locations.removeAt(index)
                    // Update the adapter directly since it's global now!
                    locationAdapter.notifyDataSetChanged()
                }
            }
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            val newName = nameInput.text.toString().trim()
            if (newName.isBlank()) {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newDesc = descInput.text.toString().trim()

            // Update the temp object
            tempLocation.name = newName
            tempLocation.description = newDesc

            // Add or Update in the global currentArea
            if (isNew) {
                currentArea.locations.add(tempLocation)
            } else {
                val index = currentArea.locations.indexOfFirst { it.id == existingLocation?.id }
                if (index != -1) {
                    currentArea.locations[index] = tempLocation
                }
            }

            // Refresh the RecyclerView
            locationAdapter.notifyDataSetChanged()
            dialog.dismiss()
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }


    private fun saveArea() {
        // 1. Sync the UI into the object
        currentArea.name = nameEt.text.toString().trim()
        currentArea.description = descEt.text.toString().trim()
        currentArea.publicInfo = publicInfoEt.text.toString().trim()
        currentArea.nsfw = nsfwSwitch.isChecked
        currentArea.private = privateSwitch.isChecked

        currentArea.timestamp = if (currentArea.timestamp == null ){
            Timestamp.now()
        } else {
            currentArea.timestamp
        }

        // 2. Validation
        if (currentArea.name.isBlank()) {
            Toast.makeText(this, "Area needs a name!", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentArea.name.length > 40) {
            Toast.makeText(this, "Area name is too long (Max 40 characters)", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentArea.description.length > 200) {
            Toast.makeText(this, "System Description is too long (Max 400 characters)", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentArea.publicInfo.length > 1000) {
            Toast.makeText(this, "Public Lore is too long (Max 1000 characters)", Toast.LENGTH_SHORT).show()
            return
        }

        showProgressDialog("Saving Area...")

        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Safe name formatter for storage paths
        val safeName: (String) -> String = { n ->
            n.ifBlank { "unnamed" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(40)
        }

        val tasks = mutableListOf<Task<*>>()

        // 3. Queue up any local images for upload
        currentArea.locations.forEach { loc ->
            val uriStr = loc.uri
            if (uriStr.isNullOrBlank()) return@forEach
            val fileUri = Uri.parse(uriStr)

            // Only upload if it's a new local file (content:// or file://)
            if (fileUri.scheme == "content" || fileUri.scheme == "file") {
                val safeLocName = safeName(loc.name) + "_" + UUID.randomUUID().toString().take(8)
                val filename = "$safeLocName.jpg"
                val storageRef = storage.reference.child("backgrounds/${currentArea.id}/$filename")

                val uploadTask = storageRef.putFile(fileUri)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                        storageRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUrl ->
                        // Swap the local URI for the permanent Firebase URL
                        loc.uri = downloadUrl.toString()
                    }
                tasks.add(uploadTask)
            }
        }

        // 4. Define the final Firestore save (runs after images finish)
        val finalSave = {
            currentArea.creatorId = userId

            // --- FEED ANNOUNCEMENT LOGIC ---
            var shouldAnnounce = false
            // Check if it's public AND hasn't been announced yet
            if (!currentArea.private && !currentArea.announced) {
                shouldAnnounce = true
                currentArea.announced = true // Flip the flag so we never announce it again!
            }

            db.collection("areas").document(currentArea.id).set(currentArea)
                .addOnSuccessListener {

                    // If we flagged it, push the event to the global timeline!
                    if (shouldAnnounce) {

                        val rawInfo = currentArea.publicInfo.trim()
                        val previewText = if (rawInfo.length > 100) rawInfo.take(100) + "..." else rawInfo

                        val feedEvent = com.albirich.RealmsAI.models.FeedEvent(
                            authorId = userId,
                            type = com.albirich.RealmsAI.models.FeedEventType.NEW_AREA,
                            title = "Published a new Area: ${currentArea.name}!",
                            content = previewText,
                            referenceId = currentArea.id,
                            timestamp = null // Firebase ServerTimestamp will fill this in automatically
                        )
                        db.collection("feed_events").document(feedEvent.id).set(feedEvent)
                    }

                    hideProgressDialog()
                    Toast.makeText(this, "Area Saved!", Toast.LENGTH_SHORT).show()
                    finish() // Exit back to the Hub!
                }
                .addOnFailureListener { e ->
                    hideProgressDialog()
                    Toast.makeText(this, "Failed to save to database: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 5. Execute
        if (tasks.isEmpty()) {
            // No new images to upload, just save data
            finalSave()
        } else {
            // Wait for all image uploads to finish, then save data
            Tasks.whenAllComplete(tasks).addOnSuccessListener {
                finalSave()
            }.addOnFailureListener { e ->
                hideProgressDialog()
                Toast.makeText(this, "Failed to upload images: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Progress Dialog Helpers ---
    private fun showProgressDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(message)
        builder.setCancelable(false)
        progressDialog = builder.create()
        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onBackPressed() {
        if (progressDialog != null && progressDialog?.isShowing == true) {
            Toast.makeText(this, "Please wait—area is still saving.", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadAreaForEditing(id: String) {
        val db = FirebaseFirestore.getInstance()

        Toast.makeText(this, "Loading Area...", Toast.LENGTH_SHORT).show()

        // We use the ID to pull the exact document from the "areas" collection
        db.collection("areas").document(id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val loadedArea = document.toObject(Area::class.java)
                    if (loadedArea != null) {
                        // 1. Set our global variable to the loaded data
                        currentArea = loadedArea

                        // 2. Populate the EditTexts
                        nameEt.setText(currentArea.name)
                        descEt.setText(currentArea.description)
                        publicInfoEt.setText(currentArea.publicInfo)
                        nsfwSwitch.isChecked = currentArea.nsfw
                        privateSwitch.isChecked = currentArea.private

                        // 3. NOW we setup the RecyclerView, because currentArea has our locations!
                        setupRecyclerView()
                    }
                } else {
                    Toast.makeText(this, "Area not found.", Toast.LENGTH_SHORT).show()
                    finish() // Boot them back to the Hub if the data is missing
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
}