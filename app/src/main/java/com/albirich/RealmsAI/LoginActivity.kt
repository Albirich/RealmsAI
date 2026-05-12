package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth


class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var tosCheckbox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // FirebaseApp was initialized in your Application class
        auth = FirebaseAuth.getInstance()

        val emailInput    = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton   = findViewById<Button>(R.id.loginButton)
        val signupButton  = findViewById<Button>(R.id.signupButton)
        tosCheckbox       = findViewById(R.id.tosCheckbox) // <--- Bind it
        val viewTosBtn    = findViewById<TextView>(R.id.viewTosButton)
        val resetPasswordBtn = findViewById<TextView>(R.id.resetPasswordButton)

        FirebaseAuth.getInstance().currentUser?.let {
            startActivity(Intent(this, ChatHubActivity::class.java))
            finish()
            return
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass  = passwordInput.text.toString().trim()
            if (email.isBlank() || pass.isBlank()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show()
            } else {
                login(email, pass)
            }
        }

        viewTosBtn.setOnClickListener {
            showTosDialog()
        }

        signupButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass  = passwordInput.text.toString().trim()

            if (!tosCheckbox.isChecked) {
                Toast.makeText(this, "You must agree to the EULA & ToS to sign up.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (email.isBlank() || pass.isBlank()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show()
            } else {
                signup(email, pass)
            }
        }

        resetPasswordBtn.setOnClickListener {
            // Pass in whatever they already typed so they don't have to type it twice
            val currentEmailInput = emailInput.text.toString().trim()
            showPasswordResetDialog(currentEmailInput)
        }

        // (Optional) print your SHA-1 once for your Google-services setup
        printSha1Fingerprint()
    }

    private fun showPasswordResetDialog(prefilledEmail: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Reset Password")
        builder.setMessage("Enter the email address associated with your account to receive a password reset link.")

        // Create an input field dynamically
        val input = EditText(this)
        input.hint = "Email Address"
        input.setText(prefilledEmail)
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        // Add some nice padding so it isn't glued to the edges
        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(64, 0, 64, 0)
        input.layoutParams = params
        container.addView(input)

        builder.setView(container)

        builder.setPositiveButton("Send Link") { dialog, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty()) {
                sendPasswordResetEmail(email)
            } else {
                Toast.makeText(this, "Please enter an email address.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun sendPasswordResetEmail(email: String) {
        Toast.makeText(this, "Sending reset link...", Toast.LENGTH_SHORT).show()

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent! Check your inbox.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun showTosDialog() {
        val tosText = """
            Realms AI Privacy Policy & Terms of Service
            Last Updated: April 16, 2026
            
            1. Data Collection Realms AI collects the following data to provide app functionality:
                
                Account Info: Email address and User ID (via Firebase Authentication) to manage your login.
                
                User Content: Characters, Chats, and Images you upload or generate. This data is stored securely on Google Firebase servers.
                
                Device Info: Basic device identifiers for crash reporting and analytics.
            
            2. Data Usage & Privacy of Content We use your data strictly to allow you to create and save characters and chats, and to sync your progress across devices. We do not sell your personal data to third parties.
                
                Your Privacy: Your private chats and private characters are strictly your own. The developers and staff of Realms AI do not monitor, read, or access your private roleplay sessions or private character definitions unless that specific content is flagged by our automated safety systems or explicitly reported for a severe Terms of Service violation.
            
            3. Permissions
            
                Storage/Photos: We request access to your photo gallery only when you choose to upload an avatar for a character or profile.
            
            4. User-Generated Content (UGC), Acceptable Use, and Reporting Realms AI is a platform for creative expression, but we maintain a strict zero-tolerance policy to ensure a safe community environment.
                
                Child Safety & Minors: The creation, uploading, or sharing of any content (including character profiles, images, or chat scenarios) that depicts, describes, or implies a minor (anyone under the age of 18) in a sexual, suggestive, or NSFW context is strictly prohibited.
                
                Reporting System: Users can report any character, chat, or user that violates our community guidelines using the in-app reporting tools.
                
                Moderation: We actively review reported content. We reserve the right to immediately remove any content and permanently ban any account that violates these terms, engages in illegal activities, or generates prohibited NSFW content involving minors, without prior notice.
            
            5. Account Deletion You have the right to delete your account and all associated data at any time. You can perform this action directly within the app settings by clicking the "Delete Account" button, which will permanently remove your data from our servers. To request the deletion of your account and all associated data without using the app, please email us at Albirich89@gmail.com with the subject line 'Account Deletion Request'.
            
            6. Contact If you have questions about this policy or need to report a severe violation directly, please contact us at: Realmsai.supp@gmail.com
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Terms of Service")
            .setMessage(tosText)
            .setPositiveButton("I Understand", null)
            .show()
    }

    private fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) goToMain()
                else          Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        Log.d("LoginActivity", "🔥 auth initialized? ${::auth.isInitialized} — instance = $auth")
    }

    private fun signup(email: String, password: String) {
        // Show a loading toast or disable button here if you want
        Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // 1. Get the newly created user
                    val user = auth.currentUser

                    if (user != null) {
                        // 2. Create their starting database profile
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val userProfile = hashMapOf(
                            "uid" to user.uid,
                            "email" to email,
                            "isPremium" to false, // Default to free tier
                            "isDev" to false,     // Default to regular user
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )

                        // 3. Save to Firestore under the 'users' collection using their UID
                        db.collection("users").document(user.uid)
                            .set(userProfile)
                            .addOnSuccessListener {
                                // Successfully created in Auth AND Firestore!
                                goToMain()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Database error: ${e.message}", Toast.LENGTH_LONG).show()
                                // You might still want to call goToMain() here since the Auth succeeded,
                                // but it's better to log the error so you know why the DB failed.
                            }
                    } else {
                        goToMain() // Fallback just in case currentUser is null
                    }
                } else {
                    Toast.makeText(this, "Signup failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun goToMain() {
        startActivity(Intent(this, ChatHubActivity::class.java))
        finish()
    }

    private fun printSha1Fingerprint() {
        try {
            val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            info.signatures!!.forEach { sig ->
            val md  = java.security.MessageDigest.getInstance("SHA1")
                val hex = md.apply { update(sig.toByteArray()) }
                    .digest()
                    .joinToString(":") { byte -> "%02X".format(byte) }
                Log.d("SHA1", hex)
            }
        } catch (e: Exception) {
            Log.e("SHA1", "Unable to get SHA1", e)
        }
    }
}
