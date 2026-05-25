package com.albirich.RealmsAI

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UpgradeActivity : BaseActivity() {

    private lateinit var billingClient: BillingClient
    private var premiumProductDetails: ProductDetails? = null
    private lateinit var btnConfirm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upgrade)

        setupBottomNav()

        // --- BIND UI ELEMENTS ---
        val btnCancel = findViewById<Button>(R.id.cancel_button)
        btnConfirm = findViewById<Button>(R.id.confirm_upgrade_button)
        val oldPrice = findViewById<TextView>(R.id.priceText)
        oldPrice.paintFlags = oldPrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

        // Disable button initially until Google loads the product
        btnConfirm.isEnabled = false
        btnConfirm.text = "Loading..."

        // --- SETUP BILLING ---
        setupBillingClient()

        // --- BUTTON ACTIONS ---
        btnCancel.setOnClickListener {
            finish()
        }

        btnConfirm.setOnClickListener {
            if (premiumProductDetails != null) {
                launchPurchaseFlow(premiumProductDetails!!)
            } else {
                Toast.makeText(this, "Still connecting to Google Play...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 1. INITIALIZE BILLING CLIENT & LISTEN FOR PURCHASES
    private fun setupBillingClient() {
        val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                Log.d("Billing", "User canceled the purchase flow.")
            } else {
                Log.e("Billing", "Error during purchase: ${billingResult.debugMessage}")
            }
        }

        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        connectToGooglePlay()
    }

    // 2. CONNECT TO GOOGLE PLAY
    private fun connectToGooglePlay() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "Connected to Google Play. Querying products...")
                    querySubscriptionProduct()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to Google Play
                Log.e("Billing", "Disconnected from Google Play.")
            }
        })
    }

    // 3. QUERY YOUR SPECIFIC PRODUCT ID
    private fun querySubscriptionProduct() {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("realms_premium_monthly") // The ID you made in the console
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                // Save the product details and enable the buy button
                premiumProductDetails = productDetailsList[0]

                runOnUiThread {
                    btnConfirm.isEnabled = true
                    btnConfirm.text = "Confirm Upgrade"
                    // Optional: You can extract the localized price here (e.g. "$9.99")
                    // and dynamically set it on your UI if you want to!
                }
            } else {
                Log.e("Billing", "No products found or error: ${billingResult.debugMessage}")
                runOnUiThread {
                    btnConfirm.text = "Unavailable"
                }
            }
        }
    }

    // 4. LAUNCH THE GOOGLE PLAY BOTTOM SHEET
    private fun launchPurchaseFlow(productDetails: ProductDetails) {
        // Subscriptions require an offer token (even if you just have a base plan)
        val offerToken = productDetails.subscriptionOfferDetails?.get(0)?.offerToken ?: return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(this, billingFlowParams)
    }

    // 5. HANDLE SUCCESSFUL PURCHASE & ACKNOWLEDGE IT
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {

            // Google REQUIRES you to acknowledge the purchase within 3 days, or they refund the user!
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // Purchase is locked in! Give the user their perks.
                        grantPremiumFeatures()
                    }
                }
            } else {
                // Already acknowledged (sometimes happens if restoring purchases)
                grantPremiumFeatures()
            }
        }
    }

    // 6. UPDATE FIRESTORE (Your original logic!)
    private fun grantPremiumFeatures() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            runOnUiThread { Toast.makeText(this, "Error: Not logged in", Toast.LENGTH_SHORT).show() }
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(user.uid)

        val updates = mapOf(
            "isPremium" to true,
            "badges" to com.google.firebase.firestore.FieldValue.arrayUnion("founder", "beta", "premium")
        )

        userRef.update(updates)
            .addOnSuccessListener {
                Log.d("UpgradeActivity", "User ${user.uid} upgraded with badges.")
                runOnUiThread {
                    Toast.makeText(this, "Welcome, Founder! Badges added.", Toast.LENGTH_LONG).show()
                    finish() // Close the paywall
                }
            }
            .addOnFailureListener { e ->
                userRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        runOnUiThread {
                            Toast.makeText(this, "Welcome, Founder! Badges added.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                    .addOnFailureListener { retryEx ->
                        Log.e("UpgradeActivity", "Firestore update failed", retryEx)
                    }
            }
    }
}