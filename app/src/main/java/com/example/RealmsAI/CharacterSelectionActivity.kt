package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class CharacterSelectionActivity : AppCompatActivity() {
    private val selectedIds = mutableSetOf<String>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_character)

        // 1) Load all chars from prefs
        fun loadCharactersFromFirestore(
            userId: String,
            onLoaded: (List<Character>) -> Unit,
            onError: (Exception) -> Unit = {}
        ) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users")
                .document(userId)
                .collection("characters")
                .get()
                .addOnSuccessListener { snapshot ->
                    val chars = snapshot.documents.mapNotNull { it.toObject(Character::class.java) }
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
        recycler.layoutManager = LinearLayoutManager(this)

        loadCharactersFromFirestore(userId = currentUserId ?: "",
            onLoaded = { allChars ->
                val adapter = CharacterSelectAdapter(allChars, selectedIds) { charId, isNowSelected ->
                    if (isNowSelected) selectedIds += charId else selectedIds -= charId
                }
                recycler.adapter = adapter
            }
        )


        // 4) Done → return list
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener {
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
