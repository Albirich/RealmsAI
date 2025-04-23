package com.example.emotichat

import android.net.Uri
import android.os.Bundle
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

    // ——— CLASS-LEVEL MODEL FOR EMOTIONS ———
    private val emotionKeys = listOf(
        "happy","sad","angry","surprised","flirty","fight","thinking","embarrassed"
    )
    private val emotionSlots = emotionKeys.map { EmotionSlot(it) }.toMutableList()
    private var currentSlotIndex = 0

    // single picker for all slots
    private lateinit var imagePicker: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        //
        // 1) COLLAPSIBLE “Private Info” SECTION
        //
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

        //
        // 2) HORIZONTAL EMOTION PICKER RECYCLER VIEW
        //
        // A) set up your image picker callback
        imagePicker = registerForActivityResult(GetContent()) { uri: Uri? ->
            uri?.let {
                emotionSlots[currentSlotIndex].uri = it
                (findViewById<RecyclerView>(R.id.emotionRecycler).adapter as? EmotionAdapter)
                    ?.notifyItemChanged(currentSlotIndex)
            }
        }

        // B) bind RecyclerView + adapter
        val emotionRecycler = findViewById<RecyclerView>(R.id.emotionRecycler)
        emotionRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        emotionRecycler.adapter = EmotionAdapter(emotionSlots) { pos ->
            currentSlotIndex = pos
            imagePicker.launch("image/*")
        }

        //
        // 3) FORM FIELDS
        //
        val nameEt         = findViewById<EditText>(R.id.characterNameInput)
        val personalityEt  = findViewById<EditText>(R.id.characterPersonalityInput)
        val tagsEt         = findViewById<EditText>(R.id.characterTagsInput)

        // private info & profile stats live inside privateSection
        val privateDescEt  = findViewById<EditText>(R.id.characterprivateDescriptionInput)
        val ageEt          = findViewById<EditText>(R.id.ageEditText)
        val heightEt       = findViewById<EditText>(R.id.heightEditText)
        val weightEt       = findViewById<EditText>(R.id.weightEditText)
        val eyeColorEt     = findViewById<EditText>(R.id.eyeColorEditText)
        val hairColorEt    = findViewById<EditText>(R.id.hairColorEditText)

        //
        // 4) SUBMIT BUTTON
        //
        findViewById<MaterialButton>(R.id.charSubmitButton).setOnClickListener {
            // -- validation --
            val name = nameEt.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // -- gather text inputs --
            val personality = personalityEt.text.toString().trim()
            val tags = tagsEt.text.toString()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val privateDesc = privateDescEt.text.toString().trim()
            val age         = ageEt.text.toString().trim()
            val height      = heightEt.text.toString().trim()
            val weight      = weightEt.text.toString().trim()
            val eyeColor    = eyeColorEt.text.toString().trim()
            val hairColor   = hairColorEt.text.toString().trim()

            // -- build JSON profile --
            val profileJson = JSONObject().apply {
                put("name", name)
                put("personality", personality)
                put("tags", JSONArray(tags))
                put("privateDescription", privateDesc)

                // profileInfo sub‐object
                put("profileInfo", JSONObject().apply {
                    put("age", age)
                    put("height", height)
                    put("weight", weight)
                    put("eyeColor", eyeColor)
                    put("hairColor", hairColor)
                })
            }

            // -- persist SharedPreferences --
            val charId = System.currentTimeMillis().toString()
            getSharedPreferences("characters", MODE_PRIVATE)
                .edit()
                .putString(charId, profileJson.toString())
                .apply()

            // -- write out each emotion image file --
            val filesDir = this.filesDir
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
