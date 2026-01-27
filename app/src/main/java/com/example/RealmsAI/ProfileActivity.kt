package com.example.RealmsAI

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.MessageStatus
import com.example.RealmsAI.models.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.WriteBatch

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
    private lateinit var deleteAccountBtn: Button
    private lateinit var verifyEmailBtn: Button
    private lateinit var statsTv: TextView

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
        messageButton = findViewById<Button>(R.id.messageButton)
        verifyEmailBtn = findViewById(R.id.verifyEmailButton)
        statsTv = findViewById(R.id.profileMessageStats)

        // In ProfileActivity.kt or DisplayProfileActivity.kt

        val upgradeBtn = findViewById<Button>(R.id.upgradeButton)

        // 1. Hide the button if they are ALREADY Premium (Optional polish)
        if (userProfile?.isPremium == true) {
            upgradeBtn.visibility = View.GONE
        } else {
            upgradeBtn.visibility = View.VISIBLE
            upgradeBtn.setOnClickListener {
                startActivity(Intent(this, UpgradeActivity::class.java))
            }
        }

        verifyEmailBtn.setOnClickListener {
            sendVerificationEmail()
        }

        // Check status immediately
        checkVerificationStatus()

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

        deleteAccountBtn = findViewById(R.id.deleteAccountButton)
        deleteAccountBtn.setOnClickListener {
            showDeleteConfirmation()
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

                    val count = doc.getLong("dailyMessageCount") ?: 0
                    val isPremium = doc.getBoolean("isPremium") ?: false

                    if (isPremium) {
                        statsTv.text = ""
                        statsTv.setTextColor(android.graphics.Color.parseColor("#FFD700")) // Gold
                    } else {
                        statsTv.text = "Messages Today:\n $count / 70"
                        // Turn red if they are close to the limit
                        if (count >= 50) {
                            statsTv.setTextColor(android.graphics.Color.RED)
                        } else {
                            statsTv.setTextColor(android.graphics.Color.WHITE)
                        }
                    }

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
    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account?")
            .setMessage("This action is PERMANENT. All your characters, chats, and data will be wiped.\n\nAre you sure?")
            .setPositiveButton("DELETE EVERYTHING") { _, _ ->
                performFullAccountDeletion()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performFullAccountDeletion() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        // Disable UI so they can't click twice
        deleteAccountBtn.isEnabled = false
        deleteAccountBtn.text = "Deleting..."

        // We use tasks to chain these operations
        // 1. QUERY CONTENT TO DELETE
        val batch = db.batch()

        // A. Find Characters
        db.collection("characters").whereEqualTo("author", uid).get()
            .addOnSuccessListener { charSnap ->
                for (doc in charSnap) {
                    batch.delete(doc.reference)
                }

                // B. Find Chats
                db.collection("chats").whereEqualTo("author", uid).get()
                    .addOnSuccessListener { chatSnap ->
                        for (doc in chatSnap) {
                            batch.delete(doc.reference)
                        }

                        // C. Delete User Profile
                        batch.delete(db.collection("users").document(uid))

                        // D. COMMIT FIRESTORE DELETES
                        batch.commit().addOnCompleteListener { batchTask ->
                            if (batchTask.isSuccessful) {
                                // E. DELETE STORAGE (Icon)
                                val iconRef = storage.reference.child("users/$uid/icon.png")
                                iconRef.delete().addOnCompleteListener {
                                    // (Ignore error if file doesn't exist)

                                    // F. DELETE AUTH ACCOUNT
                                    user.delete()
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Account deleted.", Toast.LENGTH_LONG).show()
                                            // Redirect to Login
                                            val intent = Intent(this, LoginActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                            finish()
                                        }
                                        .addOnFailureListener { e ->
                                            // If it fails (e.g., requires re-login), tell them
                                            Toast.makeText(this, "Security Error: Please Log Out and Log In again, then try deleting.", Toast.LENGTH_LONG).show()
                                            deleteAccountBtn.isEnabled = true
                                            deleteAccountBtn.text = "Delete Account"
                                        }
                                }
                            } else {
                                Toast.makeText(this, "Failed to delete data. Try again.", Toast.LENGTH_SHORT).show()
                                deleteAccountBtn.isEnabled = true
                            }
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error finding data.", Toast.LENGTH_SHORT).show()
                deleteAccountBtn.isEnabled = true
            }
    }
    private fun checkVerificationStatus() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        // 1. Reload Auth User (Vital: Firebase caches this, we need fresh data)
        user.reload().addOnSuccessListener {
            if (user.isEmailVerified) {
                // They are verified!
                verifyEmailBtn.visibility = View.GONE

                // 2. Ensure they have the badge in Firestore
                grantVerifiedBadge(user.uid)
            } else {
                // Not verified yet
                verifyEmailBtn.visibility = View.VISIBLE
            }
        }
    }

    private fun sendVerificationEmail() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        verifyEmailBtn.isEnabled = false
        verifyEmailBtn.text = "Sending..."

        user.sendEmailVerification()
            .addOnSuccessListener {
                Toast.makeText(this, "Verification email sent! Check your inbox.", Toast.LENGTH_LONG).show()
                verifyEmailBtn.text = "Email Sent"
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                verifyEmailBtn.isEnabled = true
                verifyEmailBtn.text = "Verify Email"
            }
    }

    private fun grantVerifiedBadge(userId: String) {
        val userRef = db.collection("users").document(userId)

        userRef.get().addOnSuccessListener { doc ->
            val currentBadges = doc.get("badges") as? List<String> ?: emptyList()

            // Only write if they don't have it yet (Save a database write)
            if (!currentBadges.contains("verified")) {
                userRef.update("badges", com.google.firebase.firestore.FieldValue.arrayUnion("verified"))
                    .addOnSuccessListener {
                        Toast.makeText(this, "You earned the Verified Badge!", Toast.LENGTH_SHORT).show()
                        // Reload profile to show the new icon
                        loadProfile()
                    }
            }
        }
    }
}
