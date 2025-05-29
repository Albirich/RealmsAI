package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.GridLayoutManager
import com.example.RealmsAI.models.CharacterProfile

class CharacterSelectionActivity : AppCompatActivity() {
    private val selectedIds = mutableSetOf<String>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_character)

        // 1) Load all chars from prefs
        fun loadCharactersFromFirestore(
            userId: String,
            onLoaded: (List<CharacterProfile>) -> Unit,
            onError: (Exception) -> Unit = {}
        ){
            val db = FirebaseFirestore.getInstance()
            db.collection("characters")
                .get()
                .addOnSuccessListener { snapshot ->
                    val chars = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(CharacterProfile::class.java)
                    }
                    onLoaded(chars)
                }
                .addOnFailureListener(onError)
        }


        // 2) Pre-select if passed in
        intent.getStringArrayListExtra("PRESELECTED_CHARS")?.let {
            selectedIds.addAll(it)
        }

        // 3) Setup RecyclerView + adapter
        val recycler = findViewById<RecyclerView>(R.id.characterRecycler)
        recycler.layoutManager = GridLayoutManager(this, 2)

        loadCharactersFromFirestore(userId = currentUserId ?: "",
            onLoaded = { allChars ->
                val adapter = CharacterSelectAdapter(
                    allChars,
                    selectedIds,
                    onToggle = { charId, isSelected ->
                        if (isSelected) {
                            selectedIds.add(charId)
                            Log.d("CharacterSelection", "Selected $charId")
                        } else {
                            selectedIds.remove(charId)
                            Log.d("CharacterSelection", "Deselected $charId")
                        }
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




        // 4) Done → return list
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener {
            Log.d("CharacterSelection", "Returning selected IDs: $selectedIds")
            setResult(
                RESULT_OK,
                Intent().apply {
                    putStringArrayListExtra("SELECTED_CHARS", ArrayList(selectedIds))
                }
            )
            finish()
        }

    }
}
