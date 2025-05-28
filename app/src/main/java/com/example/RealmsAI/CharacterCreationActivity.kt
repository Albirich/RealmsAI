package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Outfit
import com.example.RealmsAI.models.Relationship
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


data class CharacterDraft(
    val name: String,
    val summary: String,
    val avatarUri: String?,
    val poseUris: List<String?>,
    val backgroundUri: String?,
    val backgroundResId: Int?
)

data class FacilitatorAIResult(
    val summary: String,
    val personality: String,
    val privateDescription: String,
    val backstory: String
)


class CharacterCreationActivity : AppCompatActivity() {
    // --- UI ---
    private lateinit var avatarView: ImageView
    private lateinit var relationshipBtn: Button
    private lateinit var wardrobeButton: MaterialButton
    private lateinit var bgButton: ImageButton
    private lateinit var bgRecycler: RecyclerView

    // --- State ---
    private var progressDialog: AlertDialog? = null
    private var avatarUri: Uri? = null
    private var selectedBgUri: Uri? = null
    private var selectedBgResId: Int? = null
    private var outfitsList: List<Outfit> = emptyList()
    private var relationships: List<Relationship> = emptyList()
    private val poseKeys = listOf("happy", "sad", "angry", "embarrassed", "thinking", "flirty", "fighting", "surprised", "frightened", "exasperated")
    private val poseSlots = poseKeys.map { PoseSlot(it) }.toMutableList()
    private lateinit var bubbleColorSpinner: Spinner
    private lateinit var textColorSpinner: Spinner

    // Form fields
    private lateinit var personalityEt: EditText
    private lateinit var privateDescEt: EditText
    private lateinit var ageEt: EditText
    private lateinit var heightEt: EditText
    private lateinit var weightEt: EditText
    private lateinit var eyeColorEt: EditText
    private lateinit var hairColorEt: EditText
    private lateinit var physicalDescEt: EditText
    private lateinit var genderEt: EditText
    private lateinit var backstoryEt: EditText


    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: error("Must be signed in to create a character")

    companion object {
        private const val REQUEST_WARDROBE = 1234
        private const val RELATIONSHIP_REQ_CODE = 5001
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // Background presets
    private val presetBackgrounds = listOf(
        R.drawable.bg_beach, R.drawable.bg_castle, R.drawable.bg_comedy_club,
        R.drawable.bg_forest, R.drawable.bg_mountain_path,
        R.drawable.bg_newsroom, R.drawable.bg_office,
        R.drawable.bg_space, R.drawable.bg_woods
    )

    // ActivityResultLaunchers
    private lateinit var wardrobeLauncher: ActivityResultLauncher<Intent>
    private lateinit var avatarPicker: ActivityResultLauncher<String>
    private lateinit var bgPicker: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // --- Top-Level Views ---
        val avatarView: ImageView = findViewById(R.id.avatarImageView)
        val wardrobeButton: View = findViewById(R.id.wardrobeButton)
        val bgButton: ImageButton = findViewById(R.id.backgroundButton)
        val bgRecycler: RecyclerView = findViewById(R.id.backgroundRecycler)
        val relationshipBtn: Button = findViewById(R.id.charrelationshipBtn) // Ensure ID matches XML

        // --- Form Inputs ---
        val nameEt: EditText = findViewById(R.id.characterNameInput)
        val bioEt: EditText = findViewById(R.id.etSummary)
        personalityEt = findViewById(R.id.characterPersonalityInput)
        privateDescEt = findViewById(R.id.characterprivateDescriptionInput)
        ageEt = findViewById(R.id.ageEditText)
        heightEt = findViewById(R.id.heightEditText)
        weightEt = findViewById(R.id.weightEditText)
        eyeColorEt = findViewById(R.id.eyeColorEditText)
        hairColorEt = findViewById(R.id.hairColorEditText)
        physicalDescEt = findViewById(R.id.physicalDescriptionEditText)
        genderEt = findViewById(R.id.genderEditText)
        backstoryEt = findViewById(R.id.backstoryEditText)
        val submitBtn: MaterialButton = findViewById(R.id.charSubmitButton)
        val greetingEt: EditText = findViewById(R.id.characterGreetingInput)


        // --- Expand/Collapse Physical Info Section ---
        val physicalHeader: LinearLayout = findViewById(R.id.physicalInfoHeader)
        val physicalSection: LinearLayout = findViewById(R.id.physicalInfoSection)
        val physicalToggle: ImageView = findViewById(R.id.physicalInfoToggle)

        physicalHeader.setOnClickListener {
            val isVisible = physicalSection.visibility == View.VISIBLE
            physicalSection.visibility = if (isVisible) View.GONE else View.VISIBLE
            physicalToggle.setImageResource(
                if (isVisible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }

        // --- Relationships ---
        relationshipBtn.setOnClickListener {
            val intent = Intent(this, CharacterRelationshipActivity::class.java)
            intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
            startActivityForResult(intent, RELATIONSHIP_REQ_CODE)
        }


    // --- Avatar Picker ---
        avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                avatarUri = it
                avatarView.setImageURI(it)
            }
        }
        avatarView.setOnClickListener { avatarPicker.launch("image/*") }

        // --- Wardrobe Button ---
        wardrobeLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    result.data?.getStringExtra(EXTRA_OUTFITS_JSON)?.let { outfitsJson ->
                        outfitsList =
                            Gson().fromJson(outfitsJson, Array<Outfit>::class.java).toList()
                        Log.d("CharCreation", "Restored outfitsList = $outfitsList")
                    }
                }
            }
        wardrobeButton.setOnClickListener {
            val intent = Intent(this, WardrobeActivity::class.java)
            wardrobeLauncher.launch(intent)
        }

        // --- Background Picker ---
        bgPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedBgUri = it
                selectedBgResId = null
                bgButton.setImageURI(it)
            }
        }
        bgButton.setOnClickListener { bgPicker.launch("image/*") }

        // --- Preset Backgrounds ---
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

        bubbleColorSpinner = findViewById(R.id.bubbleColorSpinner)
        textColorSpinner = findViewById(R.id.textColorSpinner)

// Example colors (you can add more! Use hex strings for ease)
        val colorOptions = listOf(
            "#FFFFFF", // White
            "#FFEB3B", // Yellow
            "#FF9800", // Orange
            "#2196F3", // Blue
            "#E91E63", // Pink
            "#4CAF50", // Green
            "#000000"  // Black
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bubbleColorSpinner.adapter = adapter
        textColorSpinner.adapter = adapter


        submitBtn.setOnClickListener {
            val name = nameEt.text.toString().trim()
            val bio = bioEt.text.toString().trim()
            val personality = personalityEt.text.toString().trim()
            val privateDesc = privateDescEt.text.toString().trim()
            val backstory = backstoryEt.text.toString().trim()
            val age = ageEt.text.toString().toFloatOrNull() ?: 0.0f
            val height = heightEt.text.toString().trim()
            val weight = weightEt.text.toString().trim()
            val eyeColor = eyeColorEt.text.toString().trim()
            val hairColor = hairColorEt.text.toString().trim()
            val physicalDesc = physicalDescEt.text.toString().trim()
            val gender = genderEt.text.toString().trim()
            val greeting = greetingEt.text.toString().trim()


            if (name.isEmpty()) return@setOnClickListener toast("Name required")
            if (avatarUri == null) return@setOnClickListener toast("Pick an avatar")
            showProgressDialog()
            Log.d("CharCreation", "Saving outfitsList: $outfitsList")

            saveCharacterAndReturnToHub(
                name, bio, personality, privateDesc, backstory, greeting,
                age, height, weight, eyeColor, hairColor, physicalDesc, gender
            )
        }

    }
    private fun saveCharacterAndReturnToHub(
        name: String,
        summary: String,
        personality: String,
        privateDescription: String,
        backstory: String,
        greeting: String,
        age: Float,
        height: String,
        weight: String,
        eyeColor: String,
        hairColor: String,
        physicalDescription: String,
        gender: String
    ) {
        val charId = System.currentTimeMillis().toString()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val storage = FirebaseStorage.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()

        val bubbleColor = bubbleColorSpinner.selectedItem.toString()
        val textColor = textColorSpinner.selectedItem.toString()

        // --- AVATAR UPLOAD ---
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

        val uploadTasks = mutableListOf<com.google.android.gms.tasks.Task<Pair<String, String>>>()
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

        // --- POSE IMAGE UPLOADS ---
        val poseTasks = outfitsList.flatMap { outfit ->
            outfit.poseUris.mapNotNull { (poseKey, uriStr) ->
                if (uriStr.isBlank()) return@mapNotNull null
                val fileUri = Uri.parse(uriStr)
                val ext = File(fileUri.path ?: "").extension.ifBlank { "jpg" }
                val ref = storage.child("characters/$charId/poses/${outfit.name}/$poseKey.$ext")
                val uploadTask = ref.putFile(fileUri)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        ref.downloadUrl
                    }
                    .continueWith { t ->
                        Triple(outfit.name, poseKey, t.result.toString())
                    }
                uploadTask
            }
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(uploadTasks + poseTasks)
            .addOnSuccessListener { results ->
                val avatarPair = results.filterIsInstance<Pair<String, String>>().firstOrNull()
                val poseTriples = results.filterIsInstance<Triple<String, String, String>>()

                val avatarUrl = avatarPair?.second.orEmpty()

                // Map of outfitName -> Map<poseKey, url>
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

                val cloudOutfits = updatedOutfits.map { outfit ->
                    mapOf(
                        "name" to outfit.name,
                        "poseUris" to outfit.poseUris
                    )
                }

                val charData: Map<String, Any?> = mapOf(
                    "id" to charId,
                    "name" to name,
                    "summary" to summary,
                    "personality" to personality,
                    "privateDescription" to privateDescription,
                    "greeting" to greeting,
                    "backstory" to backstory,
                    "age" to age,
                    "height" to height,
                    "weight" to weight,
                    "gender" to gender,
                    "physicalDescription" to physicalDescription,
                    "eyeColor" to eyeColor,
                    "hairColor" to hairColor,
                    "bubbleColor" to bubbleColor,
                    "textColor" to textColor,
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
                    "createdAt" to FieldValue.serverTimestamp(),
                    "relationships" to relationships.map { it.copy(fromId = charId) }
                )

                firestore.collection("characters").document(charId)
                    .set(charData)
                    .addOnSuccessListener {
                        dismissProgressDialog()
                        Toast.makeText(this, "Character created!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, CreationHubActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        dismissProgressDialog()
                        toast("Failed to save character: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                dismissProgressDialog()
                toast("Upload failed: ${e.message}")
            }
    }


    // --- Relationships return handler ---
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RELATIONSHIP_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val relJson = data.getStringExtra("RELATIONSHIPS_JSON")
            if (!relJson.isNullOrEmpty()) {
                relationships = Gson().fromJson(relJson, Array<Relationship>::class.java).toList()
                toast("Loaded ${relationships.size} relationships")
            }
        }
    }

    // --- Character save logic with full wardrobe support ---
    private fun saveCharacterAndReturnToHub(
        name: String,
        bio: String,
        personality: String,
        privateDesc: String,
        age: Float,
        height: String,
        weight: String,
        eyeColor: String,
        hairColor: String
    ) {
        val charId = System.currentTimeMillis().toString()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val storage = FirebaseStorage.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()

        // --- AVATAR UPLOAD ---
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

        val uploadTasks = mutableListOf<com.google.android.gms.tasks.Task<Pair<String, String>>>()
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

        // --- POSE IMAGE UPLOADS ---
        val poseTasks = outfitsList.flatMap { outfit ->
            outfit.poseUris.mapNotNull { (poseKey, uriStr) ->
                if (uriStr.isBlank()) return@mapNotNull null
                val fileUri = Uri.parse(uriStr)
                val ext = File(fileUri.path ?: "").extension.ifBlank { "jpg" }
                val ref = storage.child("characters/$charId/poses/${outfit.name}/$poseKey.$ext")
                val uploadTask = ref.putFile(fileUri)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        ref.downloadUrl
                    }
                    .continueWith { t ->
                        Triple(outfit.name, poseKey, t.result.toString())
                    }
                uploadTask
            }
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(uploadTasks + poseTasks)
            .addOnSuccessListener { results ->
                val avatarPair = results.filterIsInstance<Pair<String, String>>().firstOrNull()
                val poseTriples = results.filterIsInstance<Triple<String, String, String>>()

                val avatarUrl = avatarPair?.second.orEmpty()

                // Map of outfitName -> Map<poseKey, url>
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

                val cloudOutfits = updatedOutfits.map { outfit ->
                    mapOf(
                        "name" to outfit.name,
                        "poseUris" to outfit.poseUris
                    )
                }

                val charData: Map<String, Any?> = mapOf(
                    "id" to charId,
                    "name" to name,
                    "personality" to personality,
                    "privateDescription" to privateDesc,
                    "age" to age,
                    "height" to height,
                    "weight" to weight,
                    "eyeColor" to eyeColor,
                    "hairColor" to hairColor,
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
                    "createdAt" to FieldValue.serverTimestamp(),
                    "relationships" to relationships.map { it.copy(fromId = charId) }
                )

                firestore.collection("characters").document(charId)
                    .set(charData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Character created!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, CreationHubActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        toast("Failed to save character: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                toast("Upload failed: ${e.message}")
            }
    }

    private fun showProgressDialog() {
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Creating Character")
            .setMessage("Please don’t close the app. Your character is being created…")
            .setCancelable(false)
            .show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    suspend fun callFacilitatorAI(prompt: String): FacilitatorAIResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY // Make sure to set your key in build.gradle
        val url = "https://api.openai.com/v1/chat/completions"

        val client = OkHttpClient()
        val json = JSONObject()
            .put("model", "gpt-4-0125-preview") // or your chosen model
            .put("messages", listOf(mapOf("role" to "user", "content" to prompt)))
            .put("max_tokens", 900)
            .toString()

        val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("OpenAI call failed: ${response.code} ${response.message}")

        val bodyStr = response.body?.string() ?: throw Exception("No response body")
        val root = JSONObject(bodyStr)
        val content = root.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        // content should be the JSON from your prompt!
        val contentJson = JSONObject(content)

        return@withContext FacilitatorAIResult(
            summary = contentJson.optString("summary"),
            personality = contentJson.optString("personality"),
            privateDescription = contentJson.optString("privateDescription"),
            backstory = contentJson.optString("backstory")
        )
    }

}
