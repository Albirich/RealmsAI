package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import kotlin.math.log

class CharacterHubActivity : BaseActivity() {
    private lateinit var sortSpinner: Spinner
    private lateinit var adapter: CharacterPreviewAdapter
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_hub)
        setupBottomNav()


        // 1) Find & configure RecyclerView
        val charsRv = findViewById<RecyclerView>(R.id.characterHubRecyclerView).apply {
            layoutManager = GridLayoutManager(this@CharacterHubActivity, 2)
        }

        adapter = CharacterPreviewAdapter(
            context = this,
            items = emptyList(),
            onClick = { preview ->
                // Get user ID
                val userId = currentUserId
                if (userId == null) {
                    Toast.makeText(this, "You must be signed in to continue.", Toast.LENGTH_SHORT).show()
                    return@CharacterPreviewAdapter
                }
                startActivity(Intent(this, SessionLandingActivity::class.java).apply {
                    Log.d("Characterhubactivity", "it has an id of: ${preview.id}")
                    putExtra("CHARACTER_ID", preview.id)
                    putExtra("CHARACTER_PROFILES_JSON", preview.rawJson)
                })
            }
        )

        charsRv.adapter = adapter

        // 3) Set up the Spinner ("Latest" vs. "Hot")
        sortSpinner = findViewById(R.id.sortSpinner)
        val options = listOf("Latest", "Hot")
        sortSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                // pos==0 → sort by createdAt; pos==1 → sort by your “hotness” field (e.g. popularity)
                val field = if (pos == 0) "createdAt" else "popularity"
                showCharacters(charsRv, orderBy = field)
            }
        }

        // 4) Initial load = “Latest”
        showCharacters(charsRv, orderBy = "createdAt")
    }

    private fun showCharacters(
        rv: RecyclerView,
        orderBy: String = "createdAt"
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "You must be signed in to view characters.", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("characters")
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    // 1) Deserialize + inject doc ID
                    val cp = doc.toObject(CharacterProfile::class.java)
                        ?.copy(id = doc.id)
                        ?: return@mapNotNull null

                    // 2) Build preview
                    CharacterPreview(
                        id        = cp.id,
                        name      = cp.name,
                        summary   = cp.summary.orEmpty(),
                        avatarUri = cp.avatarUri,
                        avatarResId = cp.avatarResId ?: R.drawable.placeholder_avatar,
                        author    = cp.author,
                        rawJson   = Gson().toJson(cp) // FULL profile as JSON!
                    )

                }
                adapter.updateList(list)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to load characters: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
