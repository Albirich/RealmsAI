package com.example.RealmsAI

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

        // (Optional) print your SHA-1 once for your Google-services setup
        printSha1Fingerprint()
    }

    private fun showTosDialog() {
        val tosText = """
            END USER LICENSE AGREEMENT (EULA) & TERMS OF SERVICE
            
            1. NO TOLERANCE FOR OBJECTIONABLE CONTENT
            Realms AI has a zero-tolerance policy for hate speech, harassment, illegal content, or excessively violent/sexual content involving minors.
            
            2. USER GENERATED CONTENT (UGC)
            Users may create and share characters and chats. You are responsible for the content you generate. Content found to violate these terms will be removed, and the user may be banned.
            
            3. REPORTING & BLOCKING
            Realms AI provides tools to report and block objectionable content and users. Reports are reviewed within 72 hours.
            
            4. AGE REQUIREMENT
            You must be at least 18 years old to use this app.
            
            By checking the box, you agree to these terms.
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
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) goToMain()
                else          Toast.makeText(this, "Signup failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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
