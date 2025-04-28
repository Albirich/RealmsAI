package com.example.emotichat

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CharacterCreationActivity : AppCompatActivity() {
    private var avatarUri: Uri? = null
    private val emotionKeys = listOf(
        "happy","sad","angry","surprised","flirty","fight","thinking","embarrassed"
    )
    private val emotionSlots = emotionKeys.map { EmotionSlot(it) }.toMutableList()
    private var currentSlotIndex = 0

    private lateinit var avatarImageView: ImageView
    private lateinit var avatarPicker: ActivityResultLauncher<String>
    private lateinit var imagePicker: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // ── Avatar picker ──
        avatarImageView = findViewById(R.id.avatarImageView)
        avatarPicker = registerForActivityResult(GetContent()) { uri ->
            uri?.let {
                avatarUri = it
                avatarImageView.setImageURI(it)
            }
        }
        avatarImageView.setOnClickListener {
            avatarPicker.launch("image/*")
        }

        // ── Private‐description collapse ──
        val privateHeader  = findViewById<LinearLayout>(R.id.privateDescHeader)
        val privateToggle  = findViewById<ImageView>(R.id.privateDescToggle)
        val privateSection = findViewById<LinearLayout>(R.id.privateSection)
        privateHeader.setOnClickListener {
            if (privateSection.visibility == View.GONE) {
                privateSection.visibility = View.VISIBLE
                privateToggle.setImageResource(R.drawable.ic_expand_less)
            } else {
                privateSection.visibility = View.GONE
                privateToggle.setImageResource(R.drawable.ic_expand_more)
            }
        }

        // ── Emotion‐slot picker ──
        imagePicker = registerForActivityResult(GetContent()) { uri ->
            uri?.let {
                emotionSlots[currentSlotIndex].uri = it
                (findViewById<RecyclerView>(R.id.emotionRecycler).adapter as? EmotionAdapter)
                    ?.notifyItemChanged(currentSlotIndex)
            }
        }
        val emotionRecycler = findViewById<RecyclerView>(R.id.emotionRecycler)
        emotionRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        emotionRecycler.adapter = EmotionAdapter(emotionSlots) { pos ->
            currentSlotIndex = pos
            imagePicker.launch("image/*")
        }

        // ── Form fields ──
        val nameEt        = findViewById<EditText>(R.id.characterNameInput)
        val personalityEt = findViewById<EditText>(R.id.characterPersonalityInput)
        val tagsEt        = findViewById<EditText>(R.id.characterTagsInput)
        val privateEt     = findViewById<EditText>(R.id.characterprivateDescriptionInput)
        val ageEt         = findViewById<EditText>(R.id.ageEditText)
        val heightEt      = findViewById<EditText>(R.id.heightEditText)
        val weightEt      = findViewById<EditText>(R.id.weightEditText)
        val eyeEt         = findViewById<EditText>(R.id.eyeColorEditText)
        val hairEt        = findViewById<EditText>(R.id.hairColorEditText)
        val greetingEt    = findViewById<EditText>(R.id.characterGreetingInput)

        // (optional) length limits
        personalityEt.filters = arrayOf(InputFilter.LengthFilter(4000))
        privateEt   .filters = arrayOf(InputFilter.LengthFilter(4000))
        greetingEt  .filters = arrayOf(InputFilter.LengthFilter(500))

        // ── Submit ──
        findViewById<MaterialButton>(R.id.charSubmitButton).setOnClickListener {
            // 1) Generate a new unique ID
            val charId = System.currentTimeMillis().toString()

            // 2) Read & split tags into a real List<String>
            val rawTags = tagsEt.text.toString().trim()
            val tagsList = if (rawTags.isEmpty()) {
                emptyList()
            } else {
                rawTags.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }

            // 3) Build our JSON object
            val profileJson = JSONObject().apply {
                put("id", charId)
                put("name", nameEt.text.toString().trim())
                put("description", personalityEt.text.toString().trim())
                put("privateDescription", privateEt.text.toString().trim())
                put("tags", JSONArray(tagsList))
                put("profileInfo", JSONObject().apply {
                    put("age", ageEt.text.toString().trim())
                    put("height", heightEt.text.toString().trim())
                    put("weight", weightEt.text.toString().trim())
                    put("eyeColor", eyeEt.text.toString().trim())
                    put("hairColor", hairEt.text.toString().trim())
                })
                put("greeting", greetingEt.text.toString().trim())

                // 3a) Save avatar into internal storage & record its URI
                avatarUri?.let { uri ->
                    val outFile = File(filesDir, "avatar_$charId.png")
                    contentResolver.openInputStream(uri)?.use { inStr ->
                        FileOutputStream(outFile).use { outStr ->
                            inStr.copyTo(outStr)
                        }
                    }
                    put("avatarUri", Uri.fromFile(outFile).toString())
                }

                // 3b) Dump your emotion‐slot URIs
                val emoJson = JSONObject()
                emotionSlots.forEach { slot ->
                    slot.uri?.let { emoJson.put(slot.key, it.toString()) }
                }
                put("emotionUris", emoJson)

                // 3c) Stamp in the author (current userId)
                val authorId = getSharedPreferences("user", Context.MODE_PRIVATE)
                    .getString("userId","")!!
                put("author", authorId)
            }

            // 4) Persist to SharedPreferences
            getSharedPreferences("characters", Context.MODE_PRIVATE)
                .edit()
                .putString(charId, profileJson.toString())
                .apply()

            // 5) Log & finish
            Log.d("CHAR_SAVE", profileJson.toString())
            finish()
        }
    }
}
