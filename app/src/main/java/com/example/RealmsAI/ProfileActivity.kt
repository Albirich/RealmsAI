package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.MessageStatus
import com.example.RealmsAI.models.UserProfile
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
    private lateinit var viewProfileButton: Button
    private lateinit var messageButton: Button

    private var iconUri: Uri? = null
    private var currentIconUrl: String? = null
    private var handle: String? = null
    private var userProfile: UserProfile? = null

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
        viewProfileButton = findViewById(R.id.viewProfileButton)

        iconView.setOnClickListener {
            pickIcon.launch("image/*")
        }

        logoutBtn.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        viewProfileButton.setOnClickListener {
            val intent = Intent(this, DisplayProfileActivity::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent)
        }

        findViewById<Button>(R.id.saveProfileButton).setOnClickListener {
            saveProfile()
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(userId)
            .collection("messages")
            .whereEqualTo("status", MessageStatus.UNOPENED.name) // If stored as string (enum name)
            .limit(1) // Only need to know if ANY exist
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    // Show badge!
                    showMessagesBadge(true)
                } else {
                    // Hide badge
                    showMessagesBadge(false)
                }
            }
            .addOnFailureListener {
                // Optionally handle errors (e.g., hide badge)
                showMessagesBadge(false)
            }

        val messageButton = findViewById<Button>(R.id.messageButton)
        messageButton.setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
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

    private fun showMessagesBadge(show: Boolean) {
        val badge = findViewById<View>(R.id.messagesBadge)
        badge.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun loadProfile() {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                userProfile = doc.toObject(UserProfile::class.java)
                userProfile?.let { profile ->
                    handle = profile.handle
                    nameEt.setText(profile.name)
                    bioEt.setText(profile.bio)
                    currentIconUrl = profile.iconUrl

                    if (profile.handle.isNullOrBlank()) {
                        handleEditContainer.visibility = View.VISIBLE
                        handleTv.visibility = View.GONE
                    } else {
                        handleTv.text = "\u0040${profile.handle}"
                        handleTv.visibility = View.VISIBLE
                        handleEditContainer.visibility = View.GONE
                    }

                    if (!profile.iconUrl.isNullOrBlank()) {
                        Glide.with(this).load(profile.iconUrl).into(iconView)
                    }
                }
            }
            .addOnFailureListener {
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
        val updatedHandle = handle  // handle is already set or locked
        val userDoc = db.collection("users").document(userId)

        fun updateProfileInFirestore(iconUrl: String?) {
            // Use current data for fields not shown on this page
            val current = userProfile
            val profile = UserProfile(
                handle = updatedHandle,
                name = name,
                bio = bio,
                iconUrl = iconUrl ?: current?.iconUrl,
                favorites = current?.favorites ?: emptyList(),
                userPicks = current?.userPicks ?: emptyList(),
                friends = current?.friends ?: emptyList(),
                pendingFriends = current?.pendingFriends ?: emptyList(),
                recentChats = current?.recentChats ?: emptyList()
            )
            userDoc.set(profile)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                    currentIconUrl = profile.iconUrl
                    userProfile = profile
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Profile update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

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
                    updateProfileInFirestore(downloadUri.toString())
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Icon upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            updateProfileInFirestore(null)
        }
    }
}
