package com.albirich.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.adapters.CharacterLinkAdapter
import com.albirich.RealmsAI.models.CharacterLink
import com.google.android.material.button.MaterialButton
import com.google.common.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class CharacterLinkActivity : AppCompatActivity() {

    private var linkedCharacters = mutableListOf<CharacterLink>()
    private lateinit var adapter: CharacterLinkAdapter
    private lateinit var characterPickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_link)

        val recycler = findViewById<RecyclerView>(R.id.linksRecycler)
        val addBtn = findViewById<MaterialButton>(R.id.addLinkBtn)
        val saveBtn = findViewById<MaterialButton>(R.id.saveLinksBtn)

        // 1. Load Existing Links
        val incomingJson = intent.getStringExtra("CHARACTER_LINKS_JSON")
        if (!incomingJson.isNullOrBlank()) {
            val type = object : TypeToken<List<CharacterLink>>() {}.type
            linkedCharacters = Gson().fromJson(incomingJson, type)
        }

        // 2. Setup Adapter
        adapter = CharacterLinkAdapter(linkedCharacters) { position ->
            linkedCharacters.removeAt(position)
            adapter.notifyItemRemoved(position)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // 3. The Launcher to grab characters
        characterPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val selectedIds = result.data?.getStringArrayListExtra("selectedCharacterIds") ?: return@registerForActivityResult

                if (selectedIds.isEmpty()) return@registerForActivityResult

                // If they picked a character, we need to quickly fetch its name from Firestore so the UI looks nice
                val targetId = selectedIds[0]

                // Prevent linking to themselves
                val currentCharId = intent.getStringExtra("CURRENT_CHAR_ID")
                if (targetId == currentCharId) {
                    Toast.makeText(this, "You cannot link a character to themselves!", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // Prevent duplicate links
                if (linkedCharacters.any { it.targetId == targetId }) {
                    Toast.makeText(this, "This character is already linked!", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                // Fetch Name from DB to populate the link card
                FirebaseFirestore.getInstance().collection("characters").document(targetId).get()
                    .addOnSuccessListener { doc ->
                        val targetName = doc.getString("name") ?: "Unknown Character"
                        val targetAvatar = doc.getString("avatarUri") ?: ""

                        // Add the new link!
                        linkedCharacters.add(CharacterLink(
                            targetId = targetId,
                            targetName = targetName,
                            targetAvatar = targetAvatar,
                            type = "switch", // Default type
                            trigger = ""
                        ))
                        adapter.notifyItemInserted(linkedCharacters.size - 1)
                        recycler.smoothScrollToPosition(linkedCharacters.size - 1)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load character details.", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // 4. Add Link Button
        addBtn.setOnClickListener {
            // Launch your existing Character Selection screen
            val intent = Intent(this, CharacterSelectionActivity::class.java)
            intent.putExtra("mode", "select")
            intent.putExtra("selectionCap", 1) // Only allow picking one at a time for links
            characterPickerLauncher.launch(intent)
        }

        // 5. Save Button
        saveBtn.setOnClickListener {
            // Clean up any empty links
            val validLinks = linkedCharacters.filter { it.targetId.isNotBlank() }

            val resultIntent = Intent()
            resultIntent.putExtra("CHARACTER_LINKS_JSON", Gson().toJson(validLinks))
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}