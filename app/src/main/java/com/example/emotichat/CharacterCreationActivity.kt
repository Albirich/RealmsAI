package com.example.emotichat

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class CharacterCreationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // Define UI elements
        val nameEditText: EditText = findViewById(R.id.characterNameInput)
        val descriptionEditText: EditText = findViewById(R.id.characterDescriptionInput)
        val tagsEditText: EditText = findViewById(R.id.characterTagsInput)
        val privateDescriptionEditText: EditText = findViewById(R.id.privateDescriptionInput)
        val avatarImageView: ImageView = findViewById(R.id.characterAvatarInput)
        val saveButton: MaterialButton = findViewById(R.id.submitButton)

        // Placeholder for selecting an avatar (can be customized)
        avatarImageView.setOnClickListener {
            // Handle avatar selection here (e.g., open gallery, take photo, or use placeholder)
            Toast.makeText(this, "Avatar clicked!", Toast.LENGTH_SHORT).show()
        }

        // Save button click handler
        saveButton.setOnClickListener {
            // Get input from user
            val name = nameEditText.text.toString().trim()
            val description = descriptionEditText.text.toString().trim()
            val tags = tagsEditText.text.toString().split(",").map { it.trim() }
            val privateDescription = privateDescriptionEditText.text.toString().trim()

            // Perform validation (make sure no fields are empty)
            if (name.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Name and Description are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Example Avatar ID (for now just static)
            val avatarResId = R.drawable.icon_01  // You can make this dynamic later based on user selection

            // Generate unique character ID (e.g., based on the current time)
            val characterId = System.currentTimeMillis().toString()

            // Create the character profile object
            val character = CharacterProfile(
                characterId = characterId,
                name = name,
                description = description,
                privateDescription = privateDescription,
                author = "User", // Or whoever created the character
                tags = tags,
                emotionTags = mapOf("happy" to "excited", "sad" to "calm"), // Example emotion tags
                avatarResId = avatarResId,
                additionalInfo = "Age: 30, Height: 6'0" // Example additional info
            )

            // Save the character profile
            saveCharacterProfile(character)

            // Go back to the previous screen or show a success message
            Toast.makeText(this, "Character Created!", Toast.LENGTH_SHORT).show()

            // Optionally, navigate back to the main screen
            finish()  // Close the activity
        }
    }

    // Save character profile to SharedPreferences
    private fun saveCharacterProfile(character: CharacterProfile) {
        val prefs = getSharedPreferences("character_profiles", MODE_PRIVATE)
        val editor = prefs.edit()

        // Create JSON object to save the character
        val characterJson = org.json.JSONObject()
        characterJson.put("name", character.name)
        characterJson.put("description", character.description)
        characterJson.put("privateDescription", character.privateDescription)
        characterJson.put("tags", org.json.JSONArray(character.tags))
        characterJson.put("emotionTags", org.json.JSONObject(character.emotionTags))
        characterJson.put("avatarResId", character.avatarResId)
        characterJson.put("additionalInfo", character.additionalInfo)

        // Save to SharedPreferences
        editor.putString(character.characterId, characterJson.toString())
        editor.apply()
    }
}
