package com.example.RealmsAI

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
                    val personas = snapshot.documents.mapNotNull { doc ->
                        val persona = doc.toObject(PersonaProfile::class.java)
                        persona?.let {
                            it.avatarUri?.let { uriStr ->
                                val uri = Uri.parse(uriStr)
                                try {
                                    contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                } catch (e: SecurityException) {
                                    // Already revoked or unavailable, ignore
                                }
                            }
                            persona
                        }
                    }
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
                val adapter = PersonaSelectAdapter(
                    allPersonas,
                    selectedIds,
                    onToggle = { personaId, isSelected ->
                        if (isSelected) selectedIds += personaId else selectedIds -= personaId
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
            val intent = Intent().apply {
                // Return the first selected persona ID, or send the whole list if multiple allowed
                putExtra("SELECTED_PERSONA_ID", selectedIds.firstOrNull())
                putStringArrayListExtra("SELECTED_PERSONAS", ArrayList(selectedIds))
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }
}
