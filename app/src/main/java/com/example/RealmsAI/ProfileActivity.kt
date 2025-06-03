package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : BaseActivity() {

    private lateinit var iconView: ImageView
    private lateinit var nameEt: EditText
    private lateinit var bioEt: EditText
    private lateinit var handleTv: TextView
    private lateinit var handleEdit: EditText
    private lateinit var handleEditContainer: LinearLayout
    private lateinit var saveHandleButton: Button
    private lateinit var logoutBtn: Button

    private var iconUri: Uri? = null
    private var currentIconUrl: String? = null
    private var handle: String? = null
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
        handleTv = findViewById(R.id.profileHandle)
        handleEdit = findViewById(R.id.handleEdit)
        handleEditContainer = findViewById(R.id.handleEditContainer)
        saveHandleButton = findViewById(R.id.saveHandleButton)
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

        saveHandleButton.setOnClickListener {
            val pickedHandle = handleEdit.text.toString().trim()
            if (pickedHandle.length < 3) {
                Toast.makeText(this, "Handle must be at least 3 characters!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkHandleUniqueAndSet(pickedHandle)
        }

        loadProfile()
    }

    private fun loadProfile() {
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            handle = doc.getString("handle")
            val name = doc.getString("name") ?: ""
            val bio = doc.getString("bio") ?: ""
            val iconUrl = doc.getString("iconUrl")
            currentIconUrl = iconUrl

            if (handle.isNullOrBlank()) {
                // Let user pick a handle (show edit)
                handleEditContainer.visibility = View.VISIBLE
                handleTv.visibility = View.GONE
            } else {
                // Show locked handle
                handleTv.text = "\u0040$handle" // \u0040 is "@"
                handleTv.visibility = View.VISIBLE
                handleEditContainer.visibility = View.GONE
            }

            nameEt.setText(name)
            bioEt.setText(bio)
            if (!iconUrl.isNullOrBlank()) Glide.with(this).load(iconUrl).into(iconView)
        }.addOnFailureListener {
            // If profile doesn't exist (new user), show handleEdit
            handleEditContainer.visibility = View.VISIBLE
            handleTv.visibility = View.GONE
        }
    }

    private fun checkHandleUniqueAndSet(newHandle: String) {
        db.collection("users")
            .whereEqualTo("handle", newHandle)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    setHandleOnce(newHandle)
                } else {
                    Toast.makeText(this, "Handle already in use. Pick another.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setHandleOnce(newHandle: String) {
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            val currentHandle = doc.getString("handle")
            if (!currentHandle.isNullOrBlank()) {
                Toast.makeText(this, "Handle already set and locked.", Toast.LENGTH_SHORT).show()
            } else {
                db.collection("users").document(userId)
                    .update("handle", newHandle)
                    .addOnSuccessListener {
                        handle = newHandle
                        handleTv.text = "\u0040$newHandle"
                        handleTv.visibility = View.VISIBLE
                        handleEditContainer.visibility = View.GONE
                        Toast.makeText(this, "Handle set: @$newHandle", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to set handle: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun saveProfile() {
        val name = nameEt.text.toString().trim()
        val bio = bioEt.text.toString().trim()
        val userDoc = db.collection("users").document(userId)

        if (iconUri != null && iconUri.toString() != currentIconUrl) {
            // Upload icon to storage
            val storageRef = FirebaseStorage.getInstance().reference
            val iconRef = storageRef.child("users/$userId/icon.png")
            iconRef.putFile(iconUri!!)
                .continueWithTask { t ->
                    if (!t.isSuccessful) throw t.exception!!
                    iconRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    userDoc.set(mapOf(
                        "name" to name,
                        "bio" to bio,
                        "iconUrl" to downloadUri.toString()
                    )).addOnSuccessListener {
                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                        currentIconUrl = downloadUri.toString()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Icon upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            userDoc.update(mapOf(
                "name" to name,
                "bio" to bio
            )).addOnSuccessListener {
                Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
