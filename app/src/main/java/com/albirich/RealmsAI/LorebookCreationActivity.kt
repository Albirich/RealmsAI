package com.albirich.RealmsAI

import android.content.Intent
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
import com.albirich.RealmsAI.adapters.LoreEntryAdapter
import com.albirich.RealmsAI.models.LoreEntry
import com.albirich.RealmsAI.models.Lorebook
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers

class LorebookCreationActivity : AppCompatActivity() {

    private lateinit var currentBook: Lorebook
    private lateinit var entryAdapter: LoreEntryAdapter

    private lateinit var coverImage: ShapeableImageView
    private lateinit var titleEt: EditText
    private lateinit var descEt: EditText
    private lateinit var recycler: RecyclerView
    private lateinit var privateSwitch: Switch

    private lateinit var imagePicker: ActivityResultLauncher<String>
    private var progressDialog: AlertDialog? = null
    private var pendingCoverUri: Uri? = null // Tracks if we need to upload a new image

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_lorebook)

        coverImage = findViewById(R.id.lorebookCoverImage)
        titleEt = findViewById(R.id.loreTitleEt)
        descEt = findViewById(R.id.loreDescEt)
        recycler = findViewById(R.id.loreEntryRecycler)
        privateSwitch = findViewById(R.id.lorePrivateSwitch)

        // 1. Initialize or Load
        val editId = intent.getStringExtra("LOREBOOK_EDIT_ID")
        if (editId != null) {
            loadLorebook(editId)
        } else {
            currentBook = Lorebook(creatorId = FirebaseAuth.getInstance().currentUser?.uid ?: "")
            setupRecyclerView()
        }

        // 2. Setup Image Picker
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                pendingCoverUri = it
                Glide.with(this).load(it).centerCrop().into(coverImage)
            }
        }

        coverImage.setOnClickListener { imagePicker.launch("image/*") }

        // 3. Add Entry Button
        findViewById<Button>(R.id.btnAddEntry).setOnClickListener {
            if (currentBook.entries.size >= 40) {
                Toast.makeText(this, "Lorebook is full! Maximum 40 entries allowed.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showEntryDialog(null)
        }

        // 4. Save Button
        findViewById<Button>(R.id.btnSaveLorebook).setOnClickListener {
            saveLorebook()
        }

        // --- BACK BUTTON INTERCEPTOR ---
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@LorebookCreationActivity)
                    .setTitle("Discard Progress?")
                    .setMessage("If you go back now, all of your unsaved lorebook details will be lost. Are you sure?")
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
        entryAdapter = LoreEntryAdapter(currentBook.entries) { clickedEntry ->
            showEntryDialog(clickedEntry)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = entryAdapter
    }

    private fun showEntryDialog(existingEntry: LoreEntry?) {
        val isNew = (existingEntry == null)
        val tempEntry = existingEntry?.copy() ?: LoreEntry()

        val dialogView = layoutInflater.inflate(R.layout.dialog_lore_entry, null)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val nameInput = dialogView.findViewById<EditText>(R.id.dialogEntryNameInput)
        val keysInput = dialogView.findViewById<EditText>(R.id.dialogKeysInput)
        val contentInput = dialogView.findViewById<EditText>(R.id.dialogContentInput)
        val saveBtn = dialogView.findViewById<Button>(R.id.dialogSaveBtn)
        val deleteBtn = dialogView.findViewById<Button>(R.id.dialogDeleteBtn)
        val cancelBtn = dialogView.findViewById<Button>(R.id.dialogCancelBtn)

        titleView.text = if (isNew) "Add Lore Entry" else "Edit Lore Entry"
        nameInput.setText(tempEntry.name)
        contentInput.setText(tempEntry.content)
        // Convert List<String> to comma-separated String for the user to edit
        keysInput.setText(tempEntry.keys.joinToString(", "))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        if (isNew) deleteBtn.visibility = View.GONE

        deleteBtn.setOnClickListener {
            if (!isNew) {
                val index = currentBook.entries.indexOfFirst { it.id == existingEntry?.id }
                if (index != -1) {
                    currentBook.entries.removeAt(index)
                    entryAdapter.notifyDataSetChanged()
                }
            }
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            val newName = nameInput.text.toString().trim()
            val newKeysString = keysInput.text.toString()
            val newContent = contentInput.text.toString().trim()

            if (newName.isBlank() || newKeysString.isBlank() || newContent.isBlank()) {
                Toast.makeText(this, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newContent.length > 500){
                Toast.makeText(this, "Content length too long. Max 500 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // MAGIC: Convert the comma-separated string back into a clean List<String>
            val parsedKeys = newKeysString.split(",")
                .map { it.trim().lowercase() } // Lowercase makes the RAG search much easier later!
                .filter { it.isNotEmpty() }

            tempEntry.name = newName
            tempEntry.keys = parsedKeys
            tempEntry.content = newContent

            if (!isNew) {
                val textChanged = (existingEntry?.content != newContent) || (existingEntry?.keys != parsedKeys)
                if (textChanged) {
                    tempEntry.embedding = null
                }
            }

            if (isNew) {
                currentBook.entries.add(tempEntry)
            } else {
                val index = currentBook.entries.indexOfFirst { it.id == existingEntry?.id }
                if (index != -1) currentBook.entries[index] = tempEntry
            }

            entryAdapter.notifyDataSetChanged()
            dialog.dismiss()
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun saveLorebook() {
       currentBook.title = titleEt.text.toString().trim()
       currentBook.description = descEt.text.toString().trim()
       currentBook.private = privateSwitch.isChecked
       currentBook.timestamp = if (currentBook.timestamp == null ){
            Timestamp.now()
        } else {
           currentBook.timestamp
        }

        if (currentBook.title.isBlank()) {
            Toast.makeText(this, "Lorebook needs a title!", Toast.LENGTH_SHORT).show()
            return
        }

        showProgressDialog("Generating Embeddings & Saving...")

        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        var shouldAnnounce = false
        // Check if it's public AND hasn't been announced yet
        if (!currentBook.private && !currentBook.announced) {
            shouldAnnounce = true
            currentBook.announced = true // Flip the flag so we never announce it again!
        }

        // 1. The final Firestore Database save
        val finalSave = {
            db.collection("lorebooks").document(currentBook.id).set(currentBook)
                .addOnSuccessListener {
                    hideProgressDialog()
                    if (shouldAnnounce) {

                        val rawInfo = currentBook.description?.trim()
                        val previewText = if (rawInfo!!.length > 100) rawInfo.take(100) + "..." else rawInfo

                        val feedEvent = com.albirich.RealmsAI.models.FeedEvent(
                            authorId = currentBook.creatorId,
                            type = com.albirich.RealmsAI.models.FeedEventType.NEW_LOREBOOK,
                            title = "Published a new Lorebook: ${currentBook.title}!",
                            content = previewText,
                            referenceId = currentBook.id,
                            timestamp = null // Firebase ServerTimestamp will fill this in automatically
                        )
                        FirestoreClient.db.collection("feed_events").document(feedEvent.id).set(feedEvent)
                    }
                    Toast.makeText(this, "Lorebook Saved!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, CreationHubActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    hideProgressDialog()
                    Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 2. The Cover Image Upload
        val uploadCoverAndSave = {
            if (pendingCoverUri != null) {
                val coverRef = storage.reference.child("lorebooks/${currentBook.id}/cover.jpg")
                coverRef.putFile(pendingCoverUri!!)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                        coverRef.downloadUrl
                    }
                    .addOnSuccessListener { downloadUrl ->
                        currentBook.coverUri = downloadUrl.toString()
                        finalSave()
                    }
                    .addOnFailureListener {
                        hideProgressDialog()
                        Toast.makeText(this, "Failed to upload cover.", Toast.LENGTH_SHORT).show()
                    }
            } else {
                finalSave()
            }
        }

        // 3. THE EMBEDDING ENGINE (Runs First!)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                for (entry in currentBook.entries) {
                    // Only embed entries that are new or were recently edited
                    if (entry.embedding == null) {
                        // We combine the keys and content so the AI has context on what this text represents
                        val textToEmbed = "Keywords: ${entry.keys.joinToString(", ")}. Lore: ${entry.content}"

                        entry.embedding = fetchEmbeddingFromAPI(textToEmbed)
                    }
                }

                // Switch back to the Main thread to handle UI and Firebase tasks
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    uploadCoverAndSave()
                }

            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    hideProgressDialog()
                    Toast.makeText(this@LorebookCreationActivity, "Embedding failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- THE EMBEDDING API HELPER ---
    private val client = OkHttpClient()

    private suspend fun fetchEmbeddingFromAPI(text: String): List<Double>? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        val mediaType = "application/json; charset=utf-8".toMediaType()

        try {
            val jsonBody = JSONObject().apply {
                put("model", "text-embedding-3-small")
                put("input", text)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/embeddings")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val dataArray = jsonResponse.getJSONArray("data")
                    val embeddingArray = dataArray.getJSONObject(0).getJSONArray("embedding")

                    // Convert JSON array into a Kotlin List<Double>
                    val vectorList = mutableListOf<Double>()
                    for (i in 0 until embeddingArray.length()) {
                        vectorList.add(embeddingArray.getDouble(i))
                    }

                    android.util.Log.d("Embeddings", "Successfully embedded Lore Entry")
                    return@withContext vectorList
                }
            } else {
                android.util.Log.e("Embeddings", "API Error: ${response.code} - ${response.body?.string()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("Embeddings", "Crash embedding text", e)
        }

        return@withContext null
    }

    private fun loadLorebook(id: String) {
        showProgressDialog("Loading...")
        FirebaseFirestore.getInstance().collection("lorebooks").document(id)
            .get()
            .addOnSuccessListener { doc ->
                hideProgressDialog()
                val loadedBook = doc.toObject(Lorebook::class.java)
                if (loadedBook != null) {
                    currentBook = loadedBook
                    titleEt.setText(currentBook.title)
                    descEt.setText(currentBook.description)
                    if(!currentBook.coverUri.isNullOrBlank()){
                        Glide.with(this)
                            .load(currentBook.coverUri)
                            .centerCrop()
                            .into(coverImage)
                    }
                    privateSwitch.isChecked = currentBook.private
                    setupRecyclerView()
                } else {
                    Toast.makeText(this, "Lorebook not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                hideProgressDialog()
                finish()
            }
    }

    private fun showProgressDialog(msg: String) {
        progressDialog = AlertDialog.Builder(this).setMessage(msg).setCancelable(false).create()
        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
    }


}