// BaseActivity.kt
package com.example.emotichat

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setupBottomNav()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear_chat -> {
                // If you want to provide a default implementation,
                // or you can forward to an abstract function that
                // child activities override if they need special behavior.
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 2) Bottom‑nav wiring (you already have this) */
    private fun setupBottomNav() {
        findViewById<ImageButton>(R.id.navChats)?.setOnClickListener {
            if (this !is ChatActivity) {
                startActivity(Intent(this, ChatActivity::class.java))
            }
        }
        findViewById<ImageButton>(R.id.navCharacters)?.setOnClickListener {
            if (this !is CharacterListActivity) {
                startActivity(Intent(this, CharacterListActivity::class.java))
            }
        }
        findViewById<ImageButton>(R.id.navCreate)?.setOnClickListener {
            if (this !is CreationHubActivity) {
                startActivity(Intent(this, CreationHubActivity::class.java))
            }
        }
        findViewById<ImageButton>(R.id.navHistory)?.setOnClickListener {
            if (this !is HistoryActivity) {
                startActivity(Intent(this, HistoryActivity::class.java))
            }
        }
        findViewById<ImageButton>(R.id.navProfile)?.setOnClickListener {
            if (this !is ProfileActivity) {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }
    }
}
