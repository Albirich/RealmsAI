package com.example.RealmsAI

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatMode
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.Outfit
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

data class CharacterDraft(
    val name: String,
    val summary: String,
    val avatarUri: String?,
    val poseUris: List<String?>,
    val backgroundUri: String?,
    val backgroundResId: Int?
)

class CharacterCreationActivity : AppCompatActivity() {
    private var avatarUri: Uri?        = null
    private var selectedBgUri: Uri?    = null
    private var selectedBgResId: Int?  = null
    private val WARDROBE_REQUEST = 42
    private lateinit var avatarPicker: ActivityResultLauncher<String>
    private val poseKeys = listOf("happy","sad","angry","surprised","flirty","thinking")
    private val poseSlots = poseKeys.map { PoseSlot(it) }.toMutableList()

    private lateinit var avatarView: ImageView
    private lateinit var bgPicker:     ActivityResultLauncher<String>
    private lateinit var bgRecycler:   RecyclerView
    private lateinit var bgButton: ImageButton

    // reuse your ChatCreation presets:
    private val presetBackgrounds = listOf(
        R.drawable.bg_beach, R.drawable.bg_castle, R.drawable.bg_comedy_club,
        R.drawable.bg_forest, R.drawable.bg_mountain_path,
        R.drawable.bg_newsroom, R.drawable.bg_office,
        R.drawable.bg_space, R.drawable.bg_woods
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // 1) Avatar picker
        val avatarView = findViewById<ImageView>(R.id.avatarImageView)
        avatarPicker = registerForActivityResult(GetContent()) { uri ->
            uri?.also {
                avatarUri = it
                avatarView.setImageURI(it)
            }
        }
        avatarView.setOnClickListener { avatarPicker.launch("image/*") }
        val wardrobeBtn = findViewById<MaterialButton>(R.id.wardrobeButton)
        wardrobeBtn.setOnClickListener {
            // 1) Gather your current draft
            val name    = findViewById<EditText>(R.id.characterNameInput).text.toString().trim()
            val summary = findViewById<EditText>(R.id.etSummary).text.toString().trim()
            val avatar  = avatarUri?.toString()
            val poses   = poseSlots.map { it.uri?.toString() }
            val bgUri   = selectedBgUri?.toString()
            val bgRes   = selectedBgResId

            val draft = CharacterDraft(name, summary, avatar, poses, bgUri, bgRes)
            val draftJson = Gson().toJson(draft)

            // 2) Save it temporarily
            getSharedPreferences("char_drafts", MODE_PRIVATE)
                .edit()
                .putString("current_draft", draftJson)
                .apply()

            // 3) Launch wardrobe screen
            Intent(this, WardrobeActivity::class.java).also { intent ->
                intent.putExtra("DRAFT_JSON", draftJson)
                startActivityForResult(intent, WARDROBE_REQUEST)
            }
        }

        findViewById<MaterialButton>(R.id.wardrobeButton)
            .setOnClickListener {
                val intent = Intent(this, WardrobeActivity::class.java)
                startActivityForResult(intent, WARDROBE_REQUEST)
            }


        // 2) Background picker button
        val bgButton = findViewById<ImageButton>(R.id.backgroundButton)
        bgPicker = registerForActivityResult(GetContent()) { uri ->
            uri?.also {
                selectedBgUri   = it
                selectedBgResId = null
                bgButton.setImageURI(it)
            }
        }
        bgButton.setOnClickListener { bgPicker.launch("image/*") }

        // 3) Preset-backgrounds carousel
        bgRecycler = findViewById(R.id.backgroundRecycler)
        bgRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bgRecycler.adapter = object: RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = presetBackgrounds.size
            override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
                object: RecyclerView.ViewHolder(ImageView(p.context).apply {
                    val size = (64 * resources.displayMetrics.density).toInt()
                    layoutParams = ViewGroup.LayoutParams(size, size)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setPadding(8,8,8,8)
                }){}
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
                val res = presetBackgrounds[i]
                (h.itemView as ImageView).apply {
                    setImageResource(res)
                    setOnClickListener {
                        selectedBgResId = res
                        selectedBgUri   = null
                        bgButton.setImageResource(res)
                    }
                }
            }
        }

        // 4) Form fields
        val nameEt  = findViewById<EditText>(R.id.characterNameInput)
        val bioEt   = findViewById<EditText>(R.id.etSummary)
        val submit  = findViewById<MaterialButton>(R.id.charSubmitButton)

        submit.setOnClickListener {
            val name = nameEt.text.toString().trim()
            val bio  = bioEt.text.toString().trim()
            if (name.isEmpty())  return@setOnClickListener toast("Name required")
            if (avatarUri == null) return@setOnClickListener toast("Pick an avatar")
            // background optional

            createCharacterAndLaunchChat(name, bio)
        }
    }

    private fun createCharacterAndLaunchChat(name: String, bio: String) {
        val charId = System.currentTimeMillis().toString()
        val now = Timestamp.now()
        // 1) Build Firestore CharacterProfile
        val charProfile = CharacterProfile(
            id           = charId,
            name         = name,
            avatarUri    = avatarUri.toString(),
            avatarResId  = null,             // gallery-only today
            background   = selectedBgUri?.toString()
                ?: selectedBgResId?.let{"android.resource://$packageName/$it"}
                ?: "",
            summary  = bio,
            createdAt    = System.currentTimeMillis()
        )
        val emotionMap = poseSlots
            .mapNotNull { slot ->
                slot.uri?.let { slot.key to it.toString() }
            }
            .toMap()

        val charProfileWithPoses = charProfile.copy(
            emotionTags = emotionMap
        )
        val db = FirebaseFirestore.getInstance()
        db.collection("characters").document(charId)
            .set(charProfileWithPoses)
            .addOnSuccessListener {
                // 2) Immediately create a one-on-one ChatProfile
                val chatId = System.currentTimeMillis().toString()
                val chatProfile = ChatProfile(
                    id = chatId,
                    title = name,
                    description = bio,
                    tags = emptyList(),
                    mode = ChatMode.ONE_ON_ONE,
                    backgroundUri = charProfile.background,
                    backgroundResId = selectedBgResId,
                    sfwOnly = true,
                    characterIds = listOf(charId),
                    rating = 0f,
                    timestamp = now,
                    author = FirebaseAuth.getInstance().currentUser!!.uid
                )
                // write chat doc
                val chatData = mapOf(
                    "title"        to chatProfile.title,
                    "description"  to chatProfile.description,
                    "tags"         to chatProfile.tags,
                    "mode"         to chatProfile.mode.name,
                    "characterIds" to chatProfile.characterIds,
                    "author"       to chatProfile.author,
                    "timestamp"    to FieldValue.serverTimestamp(),
                    "lastMessage"  to "",
                    "lastTimestamp" to FieldValue.serverTimestamp()
                )
                db.collection("chats").document(chatId)
                    .set(chatData)
                    .addOnSuccessListener {
                        // 3) Create fresh session & launch
                        SessionManager.getOrCreateSessionFor(
                            chatId,
                            onResult = { sessionId ->
                                Intent(this, MainActivity::class.java).apply {
                                    putExtra("CHAT_ID", chatId)
                                    putExtra("SESSION_ID", sessionId)
                                    putExtra("CHAT_PROFILE_JSON", Gson().toJson(chatProfile))
                                }.also{ startActivity(it) }
                                finish()
                            },
                            onError = { e ->
                                toast("Failed to start session")
                            }
                        )
                    }
                    .addOnFailureListener { e ->
                        toast("Failed to create chat")
                    }
            }
            .addOnFailureListener { e ->
                toast("Failed to save character")
            }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == WARDROBE_REQUEST && resultCode == RESULT_OK) {
            // 1) Pull back the possibly-modified draft
            val returnedJson = data?.getStringExtra("DRAFT_JSON")
                ?: getSharedPreferences("char_drafts", MODE_PRIVATE)
                    .getString("current_draft", null)

            returnedJson?.let {
                val draft = Gson().fromJson(it, CharacterDraft::class.java)

                // 2) Rehydrate your form:
                findViewById<EditText>(R.id.characterNameInput).setText(draft.name)
                findViewById<EditText>(R.id.etSummary).setText(draft.summary)
                draft.avatarUri?.let { uri -> avatarView.setImageURI(Uri.parse(uri)) }
                draft.poseUris.forEachIndexed { idx, uriStr ->
                    uriStr?.let {
                        poseSlots[idx].uri = Uri.parse(it)
                    }
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

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
