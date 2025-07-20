package com.example.RealmsAI

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.PersonaProfile
import com.example.RealmsAI.views.AddableProfileAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CharacterAdditionActivity : AppCompatActivity() {

    private lateinit var globalCharsBtn: Button
    private lateinit var personalCharsBtn: Button
    private lateinit var personasBtn: Button
    private lateinit var recycler: RecyclerView
    private lateinit var doneButton: Button

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private var selectedProfile: AddableProfile? = null
    private lateinit var adapter: AddableProfileAdapter
    private var replaceCharacterId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_character) // Update with your layout name

        globalCharsBtn = findViewById(R.id.globalChars)
        personalCharsBtn = findViewById(R.id.personalChars)
        personasBtn = findViewById(R.id.personas)
        recycler = findViewById(R.id.personaRecycler)
        doneButton = findViewById(R.id.doneButton)

        replaceCharacterId = intent.getStringExtra("replaceCharacterId")

        adapter = AddableProfileAdapter { profile ->
            selectedProfile = profile
            doneButton.isEnabled = true
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Default to showing global chars
        loadGlobalCharacters()

        globalCharsBtn.setOnClickListener { loadGlobalCharacters() }
        personalCharsBtn.setOnClickListener { loadPersonalCharacters() }
        personasBtn.setOnClickListener { loadPersonas() }

        doneButton.isEnabled = false
        doneButton.setOnClickListener {
            selectedProfile?.let { profile ->
                val resultIntent = Intent().apply {
                    when (profile) {
                        is AddableProfile.Character -> {
                            putExtra("SELECTED_TYPE", "character")
                            putExtra("SELECTED_ID", profile.profile.id)
                        }
                        is AddableProfile.Persona -> {
                            putExtra("SELECTED_TYPE", "persona")
                            putExtra("SELECTED_ID", profile.profile.id)
                        }
                    }
                    // Pass back the replacement ID if we are in replace mode!
                    replaceCharacterId?.let {
                        putExtra("replaceCharacterId", it)
                    }
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun loadGlobalCharacters() {
        db.collection("characters")
            .whereEqualTo("private", false)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                    .map { AddableProfile.Character(it) }
                adapter.submitList(list)
                selectedProfile = null
                doneButton.isEnabled = false
            }
    }

    private fun loadPersonalCharacters() {
        db.collection("characters")
            .whereEqualTo("author", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }
                    .map { AddableProfile.Character(it) }
                adapter.submitList(list)
                selectedProfile = null
                doneButton.isEnabled = false
            }
    }

    private fun loadPersonas() {
        db.collection("personas")
            .whereEqualTo("author", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.toObject(PersonaProfile::class.java) }
                    .map { AddableProfile.Persona(it) }
                adapter.submitList(list)
                selectedProfile = null
                doneButton.isEnabled = false
            }
    }
}

sealed class AddableProfile {
    data class Character(val profile: CharacterProfile) : AddableProfile()
    data class Persona(val profile: PersonaProfile) : AddableProfile()
}
