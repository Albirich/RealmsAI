package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.PersonaProfile
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class PersonaSelectionActivity : AppCompatActivity() {
    private val selectedIds = mutableSetOf<String>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_persona)

        // 1) Load all personas from prefs or Firestore
        fun loadPersonas(userId: String, onLoaded: (List<PersonaProfile>) -> Unit) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId).collection("personas")
                .get()
                .addOnSuccessListener { snapshot ->
                    val personas = snapshot.documents.mapNotNull { it.toObject(PersonaProfile::class.java) }
                    onLoaded(personas)
                }
        }


        // 2) Pre-select if passed in
        intent.getStringArrayListExtra("PRESELECTED_PERSONAS")?.let {
            selectedIds.addAll(it)
        }

        // 3) Setup RecyclerView + adapter
        val recycler = findViewById<RecyclerView>(R.id.personaRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        loadPersonas(
            userId = currentUserId ?: "",
            onLoaded = { allPersonas ->
                val adapter = PersonaSelectAdapter(allPersonas, selectedIds) { personaId, isNowSelected ->
                    if (isNowSelected) selectedIds += personaId else selectedIds -= personaId
                }
                recycler.adapter = adapter
            }
        )


        // 4) Done → return list
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener {
            setResult(
                RESULT_OK,
                Intent().apply {
                    putStringArrayListExtra("SELECTED_PERSONAS", ArrayList(selectedIds))
                }
            )
            finish()
        }
    }
}
