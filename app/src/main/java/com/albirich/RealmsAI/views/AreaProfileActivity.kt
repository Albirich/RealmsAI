package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.Area
import com.albirich.RealmsAI.models.LocationSlot
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AreaProfileActivity : AppCompatActivity() {

    private lateinit var backgroundImageView: ImageView
    private lateinit var nameView: TextView
    private lateinit var sfwBadge: ImageView
    private lateinit var authorView: TextView
    private lateinit var originalAuthorView: TextView
    private lateinit var publicInfoView: TextView
    private lateinit var locationsRecycler: RecyclerView
    private lateinit var saveButton: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_area_profile)

        // 1. Get Area ID
        val areaId = intent.getStringExtra("AREA_ID") ?: run {
            Toast.makeText(this, "No area specified!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. Find Views
        backgroundImageView = findViewById(R.id.backgroundImageView)
        nameView = findViewById(R.id.areaProfileName)
        sfwBadge = findViewById(R.id.areaProfileSfwBadge)
        authorView = findViewById(R.id.areaProfileAuthor)
        originalAuthorView = findViewById(R.id.areaOriginalProfileAuthor)
        publicInfoView = findViewById(R.id.areaProfilePublicInfo)
        locationsRecycler = findViewById(R.id.areaProfileLocationsRecycler)
        saveButton = findViewById(R.id.areaProfileSaveButton)

        // Hide original author initially
        originalAuthorView.visibility = View.GONE

        // 3. Load Data
        loadAreaProfile(areaId)
    }

    private fun loadAreaProfile(areaId: String) {
        db.collection("areas").document(areaId).get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(Area::class.java)
                if (profile == null) {
                    Toast.makeText(this, "Area not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // Header Info
                nameView.text = profile.name
                publicInfoView.text = profile.publicInfo.ifBlank { "No public lore provided." }

                // SFW Badge (Invert logic: if nsfw is false, show SFW badge)
                sfwBadge.visibility = if (!profile.nsfw) View.VISIBLE else View.GONE

                // Set Background Image to the first location's URI
                val bgUri = profile.locations.firstOrNull()?.uri
                if (!bgUri.isNullOrBlank()) {
                    Glide.with(this)
                        .load(bgUri)
                        .centerCrop()
                        .into(backgroundImageView)
                }

                // --- CURRENT AUTHOR LOOKUP ---
                val authorUserId = profile.creatorId
                db.collection("users").document(authorUserId).get().addOnSuccessListener { userDoc ->
                    val handle = userDoc.getString("handle")
                    val displayName = if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"
                    authorView.text = "by $displayName"

                    authorView.setOnClickListener {
                        val intent = Intent(this, DisplayProfileActivity::class.java)
                        intent.putExtra("userId", authorUserId)
                        startActivity(intent)
                    }
                }.addOnFailureListener {
                    authorView.text = "by (unknown)"
                }

                // --- ORIGINAL AUTHOR LOOKUP ---
                if (!profile.originalId.isNullOrBlank() && profile.id != profile.originalId) {
                    db.collection("areas").document(profile.originalId!!).get()
                        .addOnSuccessListener { origDoc ->
                            val originalArea = origDoc.toObject(Area::class.java)
                            val origAuthorId = originalArea?.creatorId ?: return@addOnSuccessListener

                            db.collection("users").document(origAuthorId).get()
                                .addOnSuccessListener { userDoc ->
                                    val handle = userDoc.getString("handle")
                                    originalAuthorView.visibility = View.VISIBLE
                                    originalAuthorView.text = "Original by ${if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"}"

                                    originalAuthorView.setOnClickListener {
                                        if (originalArea.private) {
                                            Toast.makeText(this, "Original area is set to Private.", Toast.LENGTH_SHORT).show()
                                            // Fallback to viewing the creator's profile
                                            val intent = Intent(this, DisplayProfileActivity::class.java)
                                            intent.putExtra("userId", originalArea.creatorId)
                                            startActivity(intent)
                                        } else {
                                            val intent = Intent(this, AreaProfileActivity::class.java)
                                            intent.putExtra("AREA_ID", originalArea.id)
                                            startActivity(intent)
                                        }
                                    }
                                }
                        }
                }

                // Locations Recycler
                locationsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                locationsRecycler.adapter = LocationGridAdapter(profile.locations)

                // Save Action
                saveButton.setOnClickListener {
                    if (profile.private) {
                        Toast.makeText(this, "This Area is private.", Toast.LENGTH_SHORT).show()
                    } else {
                        saveAreaAsUser(profile)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load area.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun saveAreaAsUser(area: Area) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Sign in first.", Toast.LENGTH_SHORT).show()
            return
        }

        // Keep the original link for attribution!
        val origId = area.originalId ?: area.id

        val clonedArea = area.copy(
            id = UUID.randomUUID().toString(),
            originalId = origId,
            creatorId = userId,
            private = true,
            timestamp = Timestamp.now()
        )

        db.collection("areas").document(clonedArea.id).set(clonedArea)
            .addOnSuccessListener {
                Toast.makeText(this, "Area saved to your Creation Hub!", Toast.LENGTH_SHORT).show()
                saveButton.text = "Saved ✓"
                saveButton.setBackgroundColor(android.graphics.Color.DKGRAY)
                saveButton.isEnabled = false
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save area.", Toast.LENGTH_SHORT).show()
            }
    }

    // Lightweight adapter for displaying the read-only location thumbnails
    inner class LocationGridAdapter(private val locations: List<LocationSlot>) :
        RecyclerView.Adapter<LocationGridAdapter.LocHolder>() {

        inner class LocHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.pose_image) // Reusing pose_image ID
            val label: TextView = view.findViewById(R.id.pose_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.pose_list_item, parent, false)
            return LocHolder(view)
        }

        override fun onBindViewHolder(holder: LocHolder, position: Int) {
            val loc = locations[position]
            holder.label.text = loc.name

            if (!loc.uri.isNullOrBlank()) {
                Glide.with(this@AreaProfileActivity)
                    .load(loc.uri)
                    .placeholder(R.drawable.placeholder_avatar)
                    .into(holder.image)
            } else {
                holder.image.setImageResource(R.drawable.placeholder_avatar)
            }

            // Click to see full description in a dialog
            holder.itemView.setOnClickListener {
                AlertDialog.Builder(this@AreaProfileActivity)
                    .setTitle(loc.name)
                    .setMessage(loc.description.ifBlank { "No description provided." })
                    .setPositiveButton("Close", null)
                    .show()
            }
        }

        override fun getItemCount() = locations.size
    }
}