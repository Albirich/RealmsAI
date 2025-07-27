package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class CharacterSelectionActivity : AppCompatActivity() {

    private val selectedIds = mutableSetOf<String>()
    private var selectionCap = 20
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private lateinit var counterText: TextView
    private var isTempMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_character)

        counterText = findViewById(R.id.selectionCounter)

        // Handle extras
        val preSelected = intent.getStringArrayListExtra("preSelectedIds") ?: emptyList()
        val initialCount = intent.getIntExtra("INITIAL_COUNT", 0)
        isTempMode = intent.getBooleanExtra("TEMP_SELECTION_MODE", false)
        val fromSource = intent.getStringExtra("from") ?: ""
        selectionCap = 20 - initialCount

        selectedIds.addAll(preSelected)
        updateCounter()

        val recycler = findViewById<RecyclerView>(R.id.characterRecycler)
        recycler.layoutManager = GridLayoutManager(this, 2)

        fun loadCharactersFromFirestore(
            userId: String,
            from: String,
            onLoaded: (List<CharacterProfile>) -> Unit,
            onError: (Exception) -> Unit = {}
        ) {
            val db = FirebaseFirestore.getInstance()
            val query = db.collection("characters")

            if (from == "collections") {
                // Only your characters, regardless of privacy
                query.whereEqualTo("author", userId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val chars = snapshot.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                        onLoaded(chars)
                    }
                    .addOnFailureListener(onError)
            } else if (from == "chat") {
                // Fetch public characters
                val publicQuery = query.whereNotEqualTo("private", true)
                // Fetch user's characters
                val userQuery = query.whereEqualTo("author", userId)

                publicQuery.get().continueWithTask { publicTask ->
                    val publicChars = publicTask.result?.documents?.mapNotNull { it.toObject(CharacterProfile::class.java) } ?: emptyList()
                    userQuery.get().addOnSuccessListener { userSnapshot ->
                        val userChars = userSnapshot.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }

                        // Merge and remove duplicates (based on ID)
                        val combined = (publicChars + userChars).distinctBy { it.id }
                        onLoaded(combined)
                    }.addOnFailureListener(onError)
                }.addOnFailureListener(onError)
            }
        }

        loadCharactersFromFirestore(
            userId = currentUserId ?: "",
            from = fromSource,
            onLoaded = { allChars ->
                val adapter = CharacterSelectAdapter(
                    characters = allChars,
                    selectedIds = selectedIds,
                    onToggle = { charId ->
                        if (selectedIds.contains(charId)) {
                            selectedIds.remove(charId)
                            Log.d("CharacterSelection", "Removed $charId, now: $selectedIds")
                        } else if (selectedIds.size < selectionCap) {
                            selectedIds.add(charId)
                            Log.d("CharacterSelection", "Added $charId, now: $selectedIds")
                        }
                        updateCounter()
                        recycler.adapter?.notifyItemChanged(allChars.indexOfFirst { it.id == charId })
                    },
                    loadAvatar = { imageView, avatarUri ->
                        if (!avatarUri.isNullOrEmpty()) {
                            Glide.with(imageView.context)
                                .load(avatarUri)
                                .placeholder(R.drawable.placeholder_avatar)
                                .error(R.drawable.placeholder_avatar)
                                .into(imageView)
                        } else {
                            imageView.setImageResource(R.drawable.placeholder_avatar)
                        }
                    }
                )
                recycler.adapter = adapter
            }
        )

        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener {
            val selectedList = ArrayList(selectedIds)
            Log.d("CharacterSelection", "Result intent: mode=${intent.getStringExtra("mode")}, collectionId=${intent.getStringExtra("collectionId")}, collectionName=${intent.getStringExtra("collectionName")}, selected=$selectedList")

            val result = Intent().apply {
                putStringArrayListExtra("selectedCharacterIds", selectedList)

                if (!isTempMode) {
                    val mode = intent.getStringExtra("mode")
                    if (mode == "edit") {
                        putExtra("collectionId", intent.getStringExtra("collectionId"))
                    } else if (mode == "create") {
                        putExtra("collectionName", intent.getStringExtra("collectionName"))
                    }
                }
            }
            setResult(RESULT_OK, result)
            finish()
        }

    }

    private fun updateCounter() {
        counterText.text = "Selected: ${selectedIds.size} / $selectionCap"
    }
}
