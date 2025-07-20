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
            startActivity(Intent(this, ChatCreationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_new_char).setOnClickListener {
            startActivity(Intent(this, CharacterCreationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_new_sona).setOnClickListener {
            startActivity(Intent(this, PersonaCreationActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_new_area).setOnClickListener {
            startActivity(Intent(this, BackgroundGalleryActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_view_created).setOnClickListener {
            startActivity(Intent(this, CreatedListActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_collections).setOnClickListener {
            startActivity(Intent(this, CollectionActivity::class.java))
        }
    }
}
