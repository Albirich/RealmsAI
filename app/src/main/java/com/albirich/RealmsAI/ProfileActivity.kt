package com.albirich.RealmsAI

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.MessageStatus
import com.albirich.RealmsAI.models.UserProfile
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
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
    private lateinit var deleteAccountBtn: Button
    private lateinit var verifyEmailBtn: Button
    private lateinit var statsTv: TextView
    private lateinit var shareBtn: Button

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

    private lateinit var devPanel: LinearLayout
    private lateinit var devServerSwitch: Switch
    private lateinit var devOfflineMessage: EditText
    private lateinit var devSaveServerBtn: Button

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
        devPanel = findViewById(R.id.devPanel)
        devServerSwitch = findViewById(R.id.devServerSwitch)
        devOfflineMessage = findViewById(R.id.devOfflineMessage)
        devSaveServerBtn = findViewById(R.id.devSaveServerBtn)
        shareBtn = findViewById(R.id.UserProfileShareButton)

        devSaveServerBtn.setOnClickListener {
            saveServerStatus()
        }


        // In ProfileActivity.kt or DisplayProfileActivity.k
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

        // --- SHARE BUTTON LOGIC ---
        shareBtn.setOnClickListener {
            val shareUrl = "https://realmsai.net/profile/$userId"

            // 1. Auto-copy to Clipboard
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("RealmsAI User Profile Link", shareUrl)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()

            // 2. Open the Android Share Sheet (Messages, Discord, Twitter, etc.)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Check out my profile on RealmsAI!\n$shareUrl")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Share User"))
        }

        saveHandleButton.setOnClickListener {
            val pickedHandle = handleEdit.text.toString().trim()
            if (pickedHandle.length < 3) {
                Toast.makeText(this, "Handle must be at least 3 characters!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkHandleUniqueAndSet(pickedHandle)
        }
        verifySubscriptionStatus()
        loadProfile()
    }

    private fun loadServerStatusForDev() {
        db.collection("admin").document("server_status").get()
            .addOnSuccessListener { doc ->
                val isOnline = doc.getBoolean("isOnline") ?: true
                val msg = doc.getString("offlineMessage") ?: "The AI servers are resting! We will be back soon."

                devServerSwitch.isChecked = isOnline
                devOfflineMessage.setText(msg)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load server status.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveServerStatus() {
        val isOnline = devServerSwitch.isChecked
        val msg = devOfflineMessage.text.toString().trim()

        devSaveServerBtn.isEnabled = false
        devSaveServerBtn.text = "Saving..."

        val updates = mapOf(
            "isOnline" to isOnline,
            "offlineMessage" to msg
        )

        // Using SetOptions.merge() so it creates the document if it doesn't exist yet!
        db.collection("admin").document("server_status")
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Server status updated!", Toast.LENGTH_LONG).show()
                devSaveServerBtn.isEnabled = true
                devSaveServerBtn.text = "Update Server Status"
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
                devSaveServerBtn.isEnabled = true
                devSaveServerBtn.text = "Update Server Status"
            }
    }

    private fun showMessagesBadge(show: Boolean) {
        val badge = findViewById<View>(R.id.messagesBadge)
        badge.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun loadProfile() {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Profile not found in database!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 1. Manually extract the exact fields (Bypasses the Kotlin 'is' bug)
                val isPremium = doc.getBoolean("isPremium") ?: false
                val isDev = doc.getBoolean("isDev") ?: false
                val count = doc.getLong("dailyMessageCount") ?: 0
                val loadedName = doc.getString("name") ?: ""
                val loadedBio = doc.getString("bio") ?: ""
                val loadedHandle = doc.getString("handle")
                val loadedIcon = doc.getString("iconUrl")

                // 2. Set the global variables
                handle = loadedHandle
                currentIconUrl = loadedIcon

                // 3. Update Text Inputs
                nameEt.setText(loadedName)
                bioEt.setText(loadedBio)

                // 4. --- UPGRADE BUTTON LOGIC ---
                val upgradeBtn = findViewById<Button>(R.id.upgradeButton)

                if (isPremium) {
                    upgradeBtn.visibility = View.VISIBLE
                    upgradeBtn.text = "Manage Subscription"

                    upgradeBtn.setOnClickListener {
                        // Deep link directly to the Google Play Store subscription page for RealmsAI
                        try {
                            val uri = android.net.Uri.parse("https://play.google.com/store/account/subscriptions?sku=realms_premium_monthly&package=com.albirich.RealmsAI")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            startActivity(intent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            // Fallback if the specific link fails for some reason
                            val genericUri = android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
                            startActivity(Intent(Intent.ACTION_VIEW, genericUri))
                        }
                    }

                    statsTv.text = "Unlimited"
                    statsTv.setTextColor(android.graphics.Color.parseColor("#FFD700")) // Gold

                } else {
                    upgradeBtn.visibility = View.VISIBLE
                    upgradeBtn.text = "Upgrade to Founder" // Make sure to reset the text for free users

                    upgradeBtn.setOnClickListener {
                        startActivity(Intent(this@ProfileActivity, UpgradeActivity::class.java))
                    }

                    statsTv.text = "Messages Today:\n $count / 70"
                    if (count >= 70) {
                        statsTv.setTextColor(android.graphics.Color.RED)
                    } else {
                        statsTv.setTextColor(android.graphics.Color.WHITE)
                    }
                }

                // 5. --- DEV PANEL LOGIC ---
                if (isDev) {
                    devPanel.visibility = View.VISIBLE
                    loadServerStatusForDev()
                } else {
                    devPanel.visibility = View.GONE
                }

                // 6. --- HANDLE LOGIC ---
                if (loadedHandle.isNullOrBlank()) {
                    handleEditContainer.visibility = View.VISIBLE
                    handleTv.visibility = View.GONE
                } else {
                    handleTv.text = "\u0040$loadedHandle"
                    handleTv.visibility = View.VISIBLE
                    handleEditContainer.visibility = View.GONE
                }

                // 7. --- ICON LOGIC ---
                if (!loadedIcon.isNullOrBlank()) {
                    Glide.with(this).load(loadedIcon).into(iconView)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
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
            // Build a map of ONLY the things that changed
            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "bio" to bio
            )
            updatedHandle?.let { updates["handle"] = it }
            iconUrl?.let { updates["iconUrl"] = it }

            // Use .update() instead of .set() so it doesn't wipe your isDev or isPremium flags!
            userDoc.update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                    currentIconUrl = iconUrl ?: currentIconUrl
                    // Reload the profile to get the freshest data
                    loadProfile()
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

    private fun verifySubscriptionStatus() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(user.uid)

        val billingClient = BillingClient.newBuilder(this)
            .setListener { _, _ -> } // Empty listener because we are just querying
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                    // Query Google Play for active subscriptions
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    ) { result, purchases ->

                        Log.d("BillingCheck", "Google Play found ${purchases.size} active subscriptions in cache.")
                        for (p in purchases) {
                            Log.d("BillingCheck", "Ghost Sub: ${p.products}, AutoRenewing: ${p.isAutoRenewing}")
                        }

                        val hasActiveSub = purchases.any { purchase ->
                            purchase.products.contains("realms_premium_monthly") &&
                                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }

                        // Ask Firestore what the database currently thinks your status is
                        userRef.get().addOnSuccessListener { snapshot ->
                            val currentDbPremiumStatus = snapshot.getBoolean("isPremium") ?: false

                            // If Google says NO, but Firestore says YES -> Downgrade them!
                            if (!hasActiveSub && currentDbPremiumStatus) {
                                userRef.update("isPremium", false).addOnSuccessListener {
                                    runOnUiThread {
                                        Toast.makeText(this@ProfileActivity, "Subscription expired.", Toast.LENGTH_SHORT).show()
                                        // Refresh the activity so your onCreate UI logic runs again
                                        recreate()
                                    }
                                }
                            }
                            // Failsafe: If Google says YES, but Firestore says NO -> Upgrade them!
                            else if (hasActiveSub && !currentDbPremiumStatus) {
                                userRef.update("isPremium", true).addOnSuccessListener {
                                    runOnUiThread {
                                        Toast.makeText(this@ProfileActivity, "Subscription restored.", Toast.LENGTH_SHORT).show()
                                        recreate()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Can be ignored for a simple status check
            }
        })
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
