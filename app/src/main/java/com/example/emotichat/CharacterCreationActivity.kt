package com.example.emotichat

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
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

class CharacterCreationActivity : AppCompatActivity() {

    // ──────── state for avatar + emotion slots ────────
    private var avatarUri: Uri? = null
    private lateinit var avatarPicker: ActivityResultLauncher<String>

    private val emotionKeys = listOf(
        "happy","sad","angry","surprised","flirty","fight","thinking","embarrassed"
    )
    private val emotionSlots = emotionKeys.map { EmotionSlot(it) }.toMutableList()
    private var currentSlotIndex = 0
    private lateinit var imagePicker: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // ── 1) Avatar picker ──
        val avatarImageView = findViewById<ImageView>(R.id.avatarImageView)
        avatarPicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                avatarUri = it
                avatarImageView.setImageURI(it)
            }
        }
        avatarImageView.setOnClickListener {
            avatarPicker.launch("image/*")
        }

        // ── 2) Emotion‐slot picker via RecyclerView ──
        // 2a) single gallery picker for any emotion‐slot
        imagePicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                emotionSlots[currentSlotIndex].uri = it
                // redraw only that one cell
                (findViewById<RecyclerView>(R.id.emotionRecycler)
                    .adapter as? EmotionAdapter)
                    ?.notifyItemChanged(currentSlotIndex)
            }
        }

        // 2b) wire up RecyclerView
        val emotionRecycler = findViewById<RecyclerView>(R.id.emotionRecycler)
        emotionRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        emotionRecycler.adapter = EmotionAdapter(emotionSlots) { slotIndex ->
            currentSlotIndex = slotIndex
            imagePicker.launch("image/*")
        }

        // ── 3) Collapsible “Private Info” section ──
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

        // ── 4) Grab your form fields ──
        val nameEt        = findViewById<EditText>(R.id.characterNameInput)
        val summaryEt     = findViewById<EditText>(R.id.etSummary)
        val personalityEt = findViewById<EditText>(R.id.characterPersonalityInput)
        val greetingEt    = findViewById<EditText>(R.id.characterGreetingInput)
        val tagsEt        = findViewById<EditText>(R.id.characterTagsInput)
        val privateEt     = findViewById<EditText>(R.id.characterprivateDescriptionInput)
        val ageEt         = findViewById<EditText>(R.id.ageEditText)
        val heightEt      = findViewById<EditText>(R.id.heightEditText)
        val weightEt      = findViewById<EditText>(R.id.weightEditText)
        val eyeEt         = findViewById<EditText>(R.id.eyeColorEditText)
        val hairEt        = findViewById<EditText>(R.id.hairColorEditText)
        val now = System.currentTimeMillis()
        fun CharacterCreationActivity.saveCharacterAndFinish() {
            // 5.1) new ID
            val charId = System.currentTimeMillis().toString()

            // 5.2) tags → List<String>
            val rawTags = tagsEt.text.toString().trim()
            val tags = if (rawTags.isEmpty()) emptyList()
            else rawTags.split(",").map(String::trim)

            // 5.3) build JSON
            val json = JSONObject().apply {
                put("id", charId)
                put("createdAt",      now)
                put("name", nameEt.text.toString().trim())
                put("summary", summaryEt.text.toString().trim())
                put("personality", personalityEt.text.toString().trim())
                put("greeting", greetingEt.text.toString().trim())
                put("tags", JSONArray(tags))
                put("privateDescription", privateEt.text.toString().trim())
                put("profileInfo", JSONObject().apply {
                    put("age",       ageEt.text.toString().trim())
                    put("height",    heightEt.text.toString().trim())
                    put("weight",    weightEt.text.toString().trim())
                    put("eyeColor",  eyeEt.text.toString().trim())
                    put("hairColor", hairEt.text.toString().trim())
                })

                // 5.3a) avatar → file
                avatarUri?.let { uri ->
                    val outFile = File(filesDir, "avatar_$charId.png")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            // copy from input → output
                            input.copyTo(output)
                        }
                    }
                    put("avatarUri", Uri.fromFile(outFile).toString())
                }

                // 5.3b) emotion slots → save files and map keys
                val emoObj = JSONObject()
                emotionSlots.forEach { slot ->
                    slot.uri?.let { uri ->
                        try {
                            val outFile = File(filesDir, "${slot.key}_$charId.png")
                            contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            emoObj.put(slot.key, Uri.fromFile(outFile).toString())
                            Log.d("CHAR_SAVE", "Saved emotion ${slot.key} → $outFile")
                        } catch (e: Exception) {
                            Log.w("CHAR_SAVE", "Failed to save emotion ${slot.key}", e)
                        }
                    }
                }
                put("emotionUris", emoObj)


                // 5.3c) author stamp
                val author = getSharedPreferences("user", Context.MODE_PRIVATE)
                    .getString("userId","") ?: ""
                put("author", author)
            }

            // 5.4) persist to prefs
            getSharedPreferences("characters", Context.MODE_PRIVATE)
                .edit()
                .putString(charId, json.toString())
                .apply()

            Log.d("CHAR_SAVE", json.toString())
            finish()
        }
        // ─ optional length caps ─
        summaryEt.   filters = arrayOf(InputFilter.LengthFilter(200))
        personalityEt.filters = arrayOf(InputFilter.LengthFilter(4000))
        greetingEt.  filters = arrayOf(InputFilter.LengthFilter(500))

        // ── 5) Submit button ──
        findViewById<MaterialButton>(R.id.charSubmitButton).setOnClickListener {
            val name = nameEt.text.toString().trim()
            val personality = personalityEt.text.toString().trim()
            val hasAvatar = avatarUri != null
            val hasAtLeastOneEmotion = emotionSlots.any { it.uri != null }

            when {
                name.isEmpty() -> {
                    Toast
                        .makeText(this, "Character name is required", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                personality.isEmpty() -> {
                    Toast
                        .makeText(this, "Please enter a personality", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                !hasAvatar -> {
                    Toast
                        .makeText(this, "Please select a main avatar image", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                !hasAtLeastOneEmotion -> {
                    Toast
                        .makeText(this, "Please pick at least one emotion image", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                else -> {
                    // all validations passed: build your JSON, persist & finish()
                    saveCharacterAndFinish()
                }
            }
        }
    }
}
