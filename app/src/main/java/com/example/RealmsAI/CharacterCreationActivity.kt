package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.Outfit
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

// At the top of CharacterCreationActivity.kt
data class CharacterDraft(
    val name: String,
    val summary: String,
    val avatarUri: String?,
    val poseUris: List<String?>,
    val backgroundUri: String?,
    val backgroundResId: Int?
)


class CharacterCreationActivity : AppCompatActivity() {
    private var avatarUri: Uri? = null
    private var selectedBgUri: Uri? = null
    private var selectedBgResId: Int? = null

    private lateinit var avatarPicker: ActivityResultLauncher<String>
    private lateinit var bgPicker: ActivityResultLauncher<String>
    private lateinit var bgRecycler: RecyclerView
    private lateinit var avatarView: ImageView
    private lateinit var bgButton: ImageButton

    private val poseKeys = listOf("happy", "sad", "angry", "embarrassed", "thinking", "flirty", "fighting", "surprised", "frightened", "exasperated")
    private val poseSlots = poseKeys.map { PoseSlot(it) }.toMutableList()
    private var outfitsList: List<Outfit> = emptyList()

    private lateinit var personalityEt: EditText
    private lateinit var privateDescEt: EditText
    private lateinit var ageEt:         EditText
    private lateinit var heightEt:      EditText
    private lateinit var weightEt:      EditText
    private lateinit var eyeColorEt:    EditText
    private lateinit var hairColorEt:   EditText

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: error("Must be signed in to create a character")

    companion object {
        private const val REQUEST_WARDROBE = 1234
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // Background presets (replace with your actual resources)
    private val presetBackgrounds = listOf(
        R.drawable.bg_beach, R.drawable.bg_castle, R.drawable.bg_comedy_club,
        R.drawable.bg_forest, R.drawable.bg_mountain_path,
        R.drawable.bg_newsroom, R.drawable.bg_office,
        R.drawable.bg_space, R.drawable.bg_woods
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        avatarView = findViewById(R.id.avatarImageView)
        bgButton = findViewById(R.id.backgroundButton)

        // Form fields
        val nameEt = findViewById<EditText>(R.id.characterNameInput)
        val bioEt = findViewById<EditText>(R.id.etSummary)
        personalityEt = findViewById(R.id.characterPersonalityInput)
        privateDescEt = findViewById(R.id.characterPersonalityInput)
        ageEt         = findViewById(R.id.ageEditText)
        heightEt      = findViewById(R.id.heightEditText)
        weightEt      = findViewById(R.id.weightEditText)
        eyeColorEt    = findViewById(R.id.eyeColorEditText)
        hairColorEt   = findViewById(R.id.hairColorEditText)
        val submit    = findViewById<MaterialButton>(R.id.charSubmitButton)

        // --- PHYSICAL INFO HEADER ---
        val physicalHeader = findViewById<LinearLayout>(R.id.physicalInfoHeader)
        val physicalSection = findViewById<LinearLayout>(R.id.physicalInfoSection)
        val physicalToggle = findViewById<ImageView>(R.id.physicalInfoToggle)
        physicalHeader.setOnClickListener {
            if (physicalSection.visibility == View.GONE) {
                physicalSection.visibility = View.VISIBLE
                physicalToggle.setImageResource(R.drawable.ic_expand_less)
            } else {
                physicalSection.visibility = View.GONE
                physicalToggle.setImageResource(R.drawable.ic_expand_more)
            }
        }

        // Avatar picker
        avatarPicker = registerForActivityResult(GetContent()) { uri ->
            uri?.let {
                avatarUri = it
                avatarView.setImageURI(it)
            }
        }
        avatarView.setOnClickListener { avatarPicker.launch("image/*") }

        // Wardrobe button
        findViewById<MaterialButton>(R.id.wardrobeButton)
            .setOnClickListener {
                val name = nameEt.text.toString().trim()
                val summary = bioEt.text.toString().trim()
                val avatar = avatarUri?.toString()
                val poses = poseSlots.map { it.uri?.toString() }
                val bgUri = selectedBgUri?.toString()
                val bgRes = selectedBgResId
                val draftJson = Gson().toJson(
                    CharacterDraft(
                        name, summary, avatar, poses, bgUri, bgRes
                    )
                )
            }

        // Background picker
        bgPicker = registerForActivityResult(GetContent()) { uri ->
            uri?.let {
                selectedBgUri = it
                selectedBgResId = null
                bgButton.setImageURI(it)
            }
        }
        bgButton.setOnClickListener { bgPicker.launch("image/*") }

        // Preset backgrounds
        bgRecycler = findViewById(R.id.backgroundRecycler)
        bgRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bgRecycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = presetBackgrounds.size
            override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
                object : RecyclerView.ViewHolder(ImageView(parent.context).apply {
                    val size = (64 * resources.displayMetrics.density).toInt()
                    layoutParams = ViewGroup.LayoutParams(size, size)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setPadding(8, 8, 8, 8)
                }) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, i: Int) {
                val res = presetBackgrounds[i]
                (holder.itemView as ImageView).apply {
                    setImageResource(res)
                    setOnClickListener {
                        selectedBgResId = res
                        selectedBgUri = null
                        bgButton.setImageResource(res)
                    }
                }
            }
        }

        // Submit Button
        submit.setOnClickListener {
            val name = nameEt.text.toString().trim()
            val bio = bioEt.text.toString().trim()
            val personality = personalityEt.text.toString().trim()
            val privateDescription = privateDescEt.text.toString().trim()
            val age = ageEt.text.toString().toFloatOrNull() ?: 0.0f
            val height = heightEt.text.toString().toFloatOrNull() ?: 6.0f
            val weight = weightEt.text.toString().toFloatOrNull() ?: 0.0f
            val eyeColor = eyeColorEt.text.toString().trim()
            val hairColor = hairColorEt.text.toString().trim()

            if (name.isEmpty()) return@setOnClickListener toast("Name required")
            if (avatarUri == null) return@setOnClickListener toast("Pick an avatar")

            createCharacterAndLaunchChat(name, bio, personality, privateDescription, age, height, weight, eyeColor, hairColor)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WARDROBE && resultCode == Activity.RESULT_OK) {
            data?.getStringExtra(EXTRA_OUTFITS_JSON)?.let { outfitsJson ->
                outfitsList = Gson().fromJson(outfitsJson, Array<Outfit>::class.java).toList()
            }
            Log.d("CharCreation", "Returned outfitsList = $outfitsList")
            val returnedJson = data?.getStringExtra("DRAFT_JSON")
                ?: getSharedPreferences("char_drafts", MODE_PRIVATE)
                    .getString("current_draft", null)
            returnedJson?.let {
                val draft = Gson().fromJson(it, CharacterDraft::class.java)
                findViewById<EditText>(R.id.characterNameInput).setText(draft.name)
                findViewById<EditText>(R.id.etSummary).setText(draft.summary)
                draft.avatarUri?.let { uri -> avatarView.setImageURI(Uri.parse(uri)) }
                draft.poseUris.forEachIndexed { idx, uriStr ->
                    uriStr?.let { poseSlots[idx].uri = Uri.parse(it) }
                }
                draft.backgroundUri?.let {
                    bgButton.setImageURI(Uri.parse(it))
                    selectedBgUri = Uri.parse(it)
                    selectedBgResId = null
                } ?: draft.backgroundResId?.let {
                    bgButton.setImageResource(it)
                    selectedBgResId = it
                    selectedBgUri = null
                }
            }
        }
    }

    private fun createCharacterAndLaunchChat(
        name: String,
        bio: String,
        personality: String,
        privateDesc: String,
        age: Float,
        height: Float,
        weight: Float,
        eyeColor: String,
        hairColor: String
    ) {
        val charId = System.currentTimeMillis().toString()
        val storage = FirebaseStorage.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()

        val avatarFileUri: Uri? = avatarUri?.let { originalUri ->
            val ext = contentResolver.getType(originalUri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: "jpg"
            val avatarCache = File(cacheDir, "avatar_$charId.$ext")
            contentResolver.openInputStream(originalUri)?.use { input ->
                FileOutputStream(avatarCache).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(avatarCache)
        }

        val uploadTasks = mutableListOf<Task<Pair<String, String>>>()
        avatarFileUri?.let { fileUri ->
            val ext = fileUri.path!!.substringAfterLast('.')
            val ref = storage.child("characters/$charId/avatar.$ext")
            val task = ref.putFile(fileUri)
                .continueWithTask { t ->
                    if (!t.isSuccessful) throw t.exception!!
                    ref.downloadUrl
                }
                .continueWith { t ->
                    "avatarUrl" to t.result.toString()
                }
            uploadTasks += task
        }

        // Upload all pose images with outfit names
        val poseTasks = outfitsList.flatMap { outfit ->
            outfit.poseUris.mapNotNull { (poseKey, uriStr) ->
                if (uriStr.isBlank()) return@mapNotNull null
                val fileUri = Uri.parse(uriStr)
                val ext = File(fileUri.path!!).extension.ifBlank { "jpg" }
                val ref = storage.child("characters/$charId/poses/${outfit.name}/$poseKey.$ext")
                val task = ref.putFile(fileUri)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        ref.downloadUrl
                    }
                    .continueWith { t ->
                        Triple(outfit.name, poseKey, t.result.toString())
                    }
                task
            }
        }

        // Wait for all uploads to finish
        Tasks.whenAllSuccess<Pair<String, String>>(uploadTasks + poseTasks).addOnSuccessListener { results ->
            // results: List of either Pair<String,String> or Triple<String,String,String>
            // Separate avatar upload result and pose upload results:
            val avatarPair = results.filterIsInstance<Pair<String, String>>().firstOrNull()
            val poseTriples = results.filterIsInstance<Triple<String, String, String>>()

            val avatarUrl = avatarPair?.second.orEmpty()

            // Build a map of outfitName -> Map<poseKey, url>
            val outfitsMap = mutableMapOf<String, MutableMap<String, String>>()
            for ((outfitName, poseKey, url) in poseTriples) {
                val poseMap = outfitsMap.getOrPut(outfitName) { mutableMapOf() }
                poseMap[poseKey] = url
            }

            // Build final outfits list with uploaded pose URLs
            val updatedOutfits = outfitsList.map { outfit ->
                val updatedPoses = outfitsMap[outfit.name] ?: emptyMap()
                outfit.copy(poseUris = updatedPoses)
            }

            // Prepare outfits data for Firestore
            val cloudOutfits = updatedOutfits.map { outfit ->
                mapOf(
                    "name" to outfit.name,
                    "poseUris" to outfit.poseUris
                )
            }

            // Save character data with avatar URL and outfits with pose URLs
            val charData: Map<String, Any?> = mapOf(
                "id" to charId,
                "name" to name,
                "personality" to personality,
                "privateDescription" to privateDesc,
                "age      " to age       ,
                "height   " to height    ,
                "weight   " to weight    ,
                "eyeColor " to eyeColor  ,
                "hairColor" to hairColor ,
                "author" to currentUserId,
                "tags" to emptyList<String>(),
                "emotionTags" to poseSlots.mapNotNull { s ->
                    s.uri?.let { s.key to it.toString() }
                }.toMap(),
                "outfits" to cloudOutfits,
                "currentOutfit" to cloudOutfits.firstOrNull()?.get("name")?.toString().orEmpty(),
                "avatarUri" to avatarUrl,
                "background" to (selectedBgUri?.toString()
                    ?: selectedBgResId?.let { "android.resource://$packageName/$it" }
                    ?: ""),
                "summary" to bio,
                "createdAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("characters").document(charId).set(charData)
                .addOnSuccessListener {
                    val chatCollection = firestore.collection("chats")
                    val chatDocRef = chatCollection.document()
                    val chatId = chatDocRef.id
                    val chatProfile = ChatProfile(
                        id = chatId,
                        title = name,
                        description = bio,
                        tags = emptyList(),
                        mode = ChatMode.ONE_ON_ONE,
                        backgroundUri = selectedBgUri?.toString() ?: "",
                        backgroundResId = selectedBgResId,
                        sfwOnly = true,
                        characterIds = listOf(charId),
                        rating = 0f,
                        author = currentUserId
                    )
                    val chatData = mapOf(
                        "id" to chatId,
                        "title" to name,
                        "description" to bio,
                        "tags" to emptyList<String>(),
                        "mode" to ChatMode.ONE_ON_ONE.name,
                        "backgroundUri" to (selectedBgUri?.toString() ?: ""),
                        "backgroundResId" to selectedBgResId,
                        "sfwOnly" to true,
                        "characterIds" to listOf(charId),
                        "rating" to 0f,
                        "timestamp" to Timestamp.now(),
                        "author" to currentUserId
                    )
                    chatDocRef.set(chatData)
                        .addOnSuccessListener {
                            SessionManager.getOrCreateSessionFor(chatId,
                                onResult = { sessionId ->
                                    Intent(this, SessionLandingActivity::class.java).apply {
                                        putExtra("CHAT_ID", chatId)
                                        putExtra("SESSION_ID", sessionId)
                                        putExtra("CHAT_PROFILE_JSON", Gson().toJson(chatProfile))
                                    }.also { startActivity(it) }
                                    finish()
                                },
                                onError = { e ->
                                    toast("Couldn’t start chat: ${e.message}")
                                }
                            )
                        }
                        .addOnFailureListener { e ->
                            toast("Failed to create chat doc: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    toast("Failed to save character: ${e.message}")
                }
        }
            .addOnFailureListener { e ->
                toast("Upload failed: ${e.message}")
            }
    }


}
