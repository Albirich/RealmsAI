package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast


 class CreationHubActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creation_hub)
        setupBottomNav()

        findViewById<ImageButton>(R.id.btn_new_chat).setOnClickListener {
            Log.d("CreateHub", "Character button clicked")
            Toast.makeText(this, "Clicked Character!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ChatCreationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_new_char).setOnClickListener {
            Log.d("CreateHub", "Character button clicked")
            Toast.makeText(this, "Clicked Character!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, CharacterCreationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_new_sona).setOnClickListener {
            Log.d("CreateHub", "Persona button clicked")
            Toast.makeText(this, "Clicked Persona!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PersonaCreationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_new_area).setOnClickListener {
            Log.d("CreateHub", "Background Gallery button clicked")
            Toast.makeText(this, "Opening Background Gallery!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, BackgroundGalleryActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_view_created).setOnClickListener {
            Log.d("CreateHub", "created button clicked")
            Toast.makeText(this, "Clicked Created!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, CreatedListActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_collections).setOnClickListener {
            Log.d("CreateHub", "collections button clicked")
            Toast.makeText(this, "Clicked Collections!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, CollectionActivity::class.java))
        }
    }
}
