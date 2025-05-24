package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.RealmsAI.models.Outfit
import com.example.RealmsAI.models.Relationship
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.util.UUID

class CharacterCreationActivity : AppCompatActivity() {
    // --- UI ---
    private lateinit var avatarView: ImageView
    private lateinit var relationshipBtn: Button

    // --- State ---
    private val poseKeys = listOf("happy", "sad", "angry", "embarrassed", "thinking", "flirty", "fighting", "surprised", "frightened", "exasperated")
    private val poseSlots = poseKeys.map { PoseSlot(it) }.toMutableList()
    private var outfitsList: List<Outfit> = emptyList()
    private var relationships: List<Relationship> = emptyList()

    // Form fields
    private lateinit var nameEt: EditText
    private lateinit var bioEt: EditText
    private lateinit var personalityEt: EditText
    private lateinit var privateDescEt: EditText
    private lateinit var ageEt: EditText
    private lateinit var heightEt: EditText
    private lateinit var weightEt: EditText
    private lateinit var eyeColorEt: EditText
    private lateinit var hairColorEt: EditText

    // Temp ID for this character (use once and re-use)
    private val tempCharId: String = UUID.randomUUID().toString()

    companion object {
        private const val RELATIONSHIP_REQ_CODE = 5001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // --- Bind views ---
        avatarView      = findViewById(R.id.avatarImageView)
        relationshipBtn = findViewById(R.id.relationshipBtn)
        nameEt          = findViewById(R.id.characterNameInput)
        bioEt           = findViewById(R.id.etSummary)
        personalityEt   = findViewById(R.id.characterPersonalityInput)
        privateDescEt   = findViewById(R.id.characterprivateDescriptionInput)
        ageEt           = findViewById(R.id.ageEditText)
        heightEt        = findViewById(R.id.heightEditText)
        weightEt        = findViewById(R.id.weightEditText)
        eyeColorEt      = findViewById(R.id.eyeColorEditText)
        hairColorEt     = findViewById(R.id.hairColorEditText)
        val submitBtn   = findViewById<MaterialButton>(R.id.charSubmitButton)

        // --- Launch relationship editor ---
        relationshipBtn.setOnClickListener {
            val intent = Intent(this, CharacterRelationshipActivity::class.java)
            intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
            startActivityForResult(intent, RELATIONSHIP_REQ_CODE)
        }

        // --- Submit button ---
        submitBtn.setOnClickListener {
            val name = nameEt.text.toString().trim()
            val bio  = bioEt.text.toString().trim()
            val personality = personalityEt.text.toString().trim()
            val privateDesc = privateDescEt.text.toString().trim()
            val age = ageEt.text.toString().toIntOrNull() ?: 0
            val height = heightEt.text.toString()
            val weight = weightEt.text.toString()
            val eyeColor = eyeColorEt.text.toString().trim()
            val hairColor = hairColorEt.text.toString().trim()

            // Build your CharacterProfile or a simple map for Firestore
            val charData = hashMapOf(
                "id" to tempCharId,
                "name" to name,
                "personality" to personality,
                "privateDescription" to privateDesc,
                "age" to age,
                "height" to height,
                "weight" to weight,
                "eyeColor" to eyeColor,
                "hairColor" to hairColor,
                "relationships" to relationships.map { it.copy(fromId = tempCharId) },
                // ... add other fields as needed
            )

            FirebaseFirestore.getInstance().collection("characters")
                .document(tempCharId)
                .set(charData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Character created!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // --- Handle return from relationship editor ---
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RELATIONSHIP_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val relJson = data.getStringExtra("RELATIONSHIPS_JSON")
            if (!relJson.isNullOrEmpty()) {
                relationships = Gson().fromJson(relJson, Array<Relationship>::class.java).toList()
                Toast.makeText(this, "Loaded ${relationships.size} relationships", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
