package com.example.RealmsAI

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class UpgradeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upgrade)

        // 2. Call your BaseActivity's navigation setup
        setupBottomNav()

        // --- BIND UI ELEMENTS ---
        val btnCancel = findViewById<Button>(R.id.cancel_button)
        val btnConfirm = findViewById<Button>(R.id.confirm_upgrade_button)
        val oldPrice = findViewById<TextView>(R.id.priceText)
        oldPrice.paintFlags = oldPrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

        // (We don't need to manually bind navHome/navAccount etc here
        // because setupBottomNav() usually handles that for you!)

        // --- BUTTON ACTIONS ---

        // Cancel Button: Go back to previous screen
        btnCancel.setOnClickListener {
            finish()
        }

        // Confirm Upgrade (Alpha Testing Mode)
        btnConfirm.setOnClickListener {
            performAlphaUpgrade()
        }
    }

    private fun performAlphaUpgrade() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Error: Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val btnConfirm = findViewById<Button>(R.id.confirm_upgrade_button)
        btnConfirm.isEnabled = false
        btnConfirm.text = "Upgrading..."

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(user.uid)

        // PREPARE UPDATES
        // 1. Set Premium to true
        // 2. Add all 3 badges to the 'badges' array
        val updates = mapOf(
            "isPremium" to true,
            "badges" to com.google.firebase.firestore.FieldValue.arrayUnion("founder", "beta", "premium")
        )

        userRef.update(updates)
            .addOnSuccessListener {
                Log.d("UpgradeActivity", "User ${user.uid} upgraded with badges.")
                Toast.makeText(this, "Welcome, Founder! Badges added.", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                // Fallback: If the user doc doesn't exist yet (rare), create it with set()
                userRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Welcome, Founder! Badges added.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener { retryEx ->
                        Log.e("UpgradeActivity", "Upgrade failed", retryEx)
                        Toast.makeText(this, "Upgrade failed: ${retryEx.message}", Toast.LENGTH_SHORT).show()
                        btnConfirm.isEnabled = true
                        btnConfirm.text = "Confirm Upgrade"
                    }
            }
    }

    // If your BaseActivity requires you to define which nav item is selected:
    // override fun getNavigationMenuItemId() = R.id.nav_none
    // (Or whichever ID represents 'no selection' or 'settings' in your app)
}