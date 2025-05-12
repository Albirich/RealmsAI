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
import com.example.RealmsAI.CharacterProfile
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class CharacterCreationActivity : AppCompatActivity() {
    private var avatarUri: Uri?        = null
    private var selectedBgUri: Uri?    = null
    private var selectedBgResId: Int?  = null

    private lateinit var avatarPicker: ActivityResultLauncher<String>
    private val poseKeys = listOf("happy","sad","angry","surprised","flirty","thinking")
    private val poseSlots = poseKeys.map { PoseSlot(it) }.toMutableList()
    private var currentPoseIndex = 0
    private lateinit var posePicker: ActivityResultLauncher<String>

    private lateinit var bgPicker:     ActivityResultLauncher<String>
    private lateinit var bgRecycler:   RecyclerView

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
        // Pose‐slot gallery picker:
        posePicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.also {
                poseSlots[currentPoseIndex].uri = it
                // notify only that one item changed
                (findViewById<RecyclerView>(R.id.poseRecycler).adapter as PoseAdapter)
                    .notifyItemChanged(currentPoseIndex)
            }
        }

// RecyclerView for your poses:
        val poseRecycler = findViewById<RecyclerView>(R.id.poseRecycler)
        poseRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        poseRecycler.adapter = PoseAdapter(poseSlots) { slotIndex ->
            currentPoseIndex = slotIndex
            posePicker.launch("image/*")
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
                    id             = chatId,
                    title          = name,
                    description    = bio,
                    tags           = emptyList(),
                    mode           = ChatMode.ONE_ON_ONE,
                    backgroundUri  = charProfile.background,
                    backgroundResId= selectedBgResId,
                    sfwOnly        = true,
                    characterIds   = listOf(charId),
                    rating         = 0f,
                    timestamp      = System.currentTimeMillis(),
                    author         = FirebaseAuth.getInstance().currentUser!!.uid
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

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
