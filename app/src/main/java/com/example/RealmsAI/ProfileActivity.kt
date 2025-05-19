package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : BaseActivity() {

    private lateinit var iconView: ImageView
    private lateinit var nameEt: EditText
    private lateinit var bioEt: EditText
    private lateinit var handleTv: TextView
    private lateinit var logoutBtn: Button

    private var iconUri: Uri? = null
    private val db = FirebaseFirestore.getInstance()
    private val userId: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val pickIcon = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            iconUri = it
            Glide.with(this).load(it).into(iconView)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        setupBottomNav()


        iconView = findViewById(R.id.profileIcon)
        nameEt = findViewById(R.id.profileName)
        bioEt = findViewById(R.id.profileBio)
        logoutBtn = findViewById(R.id.logoutButton)

        iconView.setOnClickListener {
            pickIcon.launch("image/*")
        }

        logoutBtn.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.saveProfileButton).setOnClickListener {
            saveProfile()
        }

        loadProfile()
    }

    private fun loadProfile() {
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            val handle = doc.getString("handle") ?: userId.take(8)
            val name = doc.getString("name") ?: ""
            val bio = doc.getString("bio") ?: ""
            val iconUrl = doc.getString("iconUrl")

            handleTv.text = "@$handle"
            nameEt.setText(name)
            bioEt.setText(bio)
            if (!iconUrl.isNullOrBlank()) Glide.with(this).load(iconUrl).into(iconView)
        }
    }

    private fun saveProfile() {
        val name = nameEt.text.toString().trim()
        val bio = bioEt.text.toString().trim()
        val userDoc = db.collection("users").document(userId)
        if (iconUri != null) {
            // Optional: upload image to storage, then save URL.
            // For now, just save the URI string for demo:
            userDoc.update(mapOf(
                "name" to name,
                "bio" to bio,
                "iconUrl" to iconUri.toString()
            ))
        } else {
            userDoc.update(mapOf(
                "name" to name,
                "bio" to bio
            ))
        }
        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
    }
}
