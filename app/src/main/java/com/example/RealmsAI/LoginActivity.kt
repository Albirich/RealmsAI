package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth


class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // FirebaseApp was initialized in your Application class
        auth = FirebaseAuth.getInstance()

        val emailInput    = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton   = findViewById<Button>(R.id.loginButton)
        val signupButton  = findViewById<Button>(R.id.signupButton)

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

        signupButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass  = passwordInput.text.toString().trim()
            if (email.isBlank() || pass.isBlank()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show()
            } else {
                signup(email, pass)
            }
        }

        // (Optional) print your SHA-1 once for your Google-services setup
        printSha1Fingerprint()
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
