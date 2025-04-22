package com.example.emotichat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast


 class CreationHubActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creation_hub)

        findViewById<ImageButton>(R.id.btn_new_char).setOnClickListener {
            Log.d("CreateHub", "Character button clicked")
            Toast.makeText(this, "Clicked Character!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, CharacterCreationActivity::class.java))
        }
    }
}
