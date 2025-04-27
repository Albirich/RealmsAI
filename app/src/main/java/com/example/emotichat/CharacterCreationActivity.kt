package com.example.emotichat

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
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

        // --- Avatar picker setup ---
        avatarImageView = findViewById(R.id.avatarImageView)
        avatarPicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                avatarUri = it
                avatarImageView.setImageURI(it)
            }
        }
        avatarImageView.setOnClickListener { avatarPicker.launch("image/*") }

        // --- Collapsible Private Info Section ---
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

        // --- Horizontal emotion‐picker RecyclerView ---
        imagePicker = registerForActivityResult(GetContent()) { uri: Uri? ->
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

        // --- Form fields ---
        val nameEt         = findViewById<EditText>(R.id.characterNameInput)
        val personalityEt  = findViewById<EditText>(R.id.characterPersonalityInput)
        val tagsEt         = findViewById<EditText>(R.id.characterTagsInput)
        val privateDescEt  = findViewById<EditText>(R.id.characterprivateDescriptionInput)
        val ageEt          = findViewById<EditText>(R.id.ageEditText)
        val heightEt       = findViewById<EditText>(R.id.heightEditText)
        val weightEt       = findViewById<EditText>(R.id.weightEditText)
        val eyeColorEt     = findViewById<EditText>(R.id.eyeColorEditText)
        val hairColorEt    = findViewById<EditText>(R.id.hairColorEditText)
        val greetingEt = findViewById<EditText>(R.id.characterGreetingInput)

        val maxProfileChars  = 4000
        val maxGreetingChars = 500
        personalityEt.filters = arrayOf(InputFilter.LengthFilter(maxProfileChars))
        privateDescEt.filters = arrayOf(InputFilter.LengthFilter(maxProfileChars))
        greetingEt.filters    = arrayOf(InputFilter.LengthFilter(maxGreetingChars))

        // --- Submit button ---
        findViewById<MaterialButton>(R.id.charSubmitButton)
            .setOnClickListener {
                // 1) Validation
                val name = nameEt.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 2) Gather inputs
                val personality = personalityEt.text.toString().trim()
                val tags = tagsEt.text.toString()
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val privateDesc = privateDescEt.text.toString().trim()
                val age         = ageEt.text.toString().trim()
                val height      = heightEt.text.toString().trim()
                val weight      = weightEt.text.toString().trim()
                val eyeColor    = eyeColorEt.text.toString().trim()
                val hairColor   = hairColorEt.text.toString().trim()

                // 3) Build JSON profile
                val profileJson = JSONObject().apply {
                    put("name", name)
                    put("personality", personality)
                    put("tags", JSONArray(tags))
                    put("privateDescription", privateDesc)
                    put("profileInfo", JSONObject().apply {
                        put("age", age)
                        put("height", height)
                        put("weight", weight)
                        put("eyeColor", eyeColor)
                        put("hairColor", hairColor)
                    })
                    // 3a) stamp in the current userId as “author”
                    val authorId = getSharedPreferences("user", Context.MODE_PRIVATE)
                        .getString("userId", "")!!
                    put("author", authorId)
                }

                // 4) Persist SharedPreferences
                val charId = System.currentTimeMillis().toString()

                // 5) Save avatar to internal storage & record URI
                avatarUri?.let { uri ->
                    val outFile = File(filesDir, "avatar_${charId}.png")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    profileJson.put("avatarUri", Uri.fromFile(outFile).toString())
                }

                // 6) Write the JSON into your “characters” prefs
                getSharedPreferences("characters", Context.MODE_PRIVATE)
                    .edit()
                    .putString(charId, profileJson.toString())
                    .apply()

                // 7) Write out each emotion image to filesDir
                emotionSlots.forEach { slot ->
                    slot.uri?.let { uri ->
                        val filename = "${slot.key}_${charId}.png"
                        val outFile  = File(filesDir, filename)
                        contentResolver.openInputStream(uri)?.use { inp ->
                            FileOutputStream(outFile).use { out -> inp.copyTo(out) }
                        }
                        profileJson.put("emotionImage_${slot.key}", outFile.absolutePath)
                    }
                }

                Toast.makeText(this, "Character created!", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
}
