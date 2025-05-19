package com.example.RealmsAI

import SessionPreview
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson

class SessionHubActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_hub)
        setupBottomNav()

        val rv = findViewById<RecyclerView>(R.id.sessionRecycler)
        rv.layoutManager = LinearLayoutManager(this)

        showSessions(rv)
    }

    private fun showSessions(rv: RecyclerView) {
        val userId = currentUserId
        if (userId == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("sessions")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    SessionPreview(
                        id = doc.id,
                        title = data["title"] as? String ?: "(Untitled Session)",
                        chatId = data["chatId"] as? String ?: "",
                        timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.seconds ?: 0L,
                        rawJson = Gson().toJson(data)
                    )
                }

                rv.adapter = SessionPreviewAdapter(
                    context = this,
                    sessionList = previews,
                    onClick = { preview ->
                        // You can launch MainActivity for this session/chat here
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            putExtra("SESSION_ID", preview.id)
                            putExtra("CHAT_ID", preview.chatId)
                            putExtra("SESSION_JSON", preview.rawJson)
                        })
                    }
                )
            }
            .addOnFailureListener { e ->
                Log.e("SessionHub", "fetch failed", e)
                Toast.makeText(this, "Failed to load sessions: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
