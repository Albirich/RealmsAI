package com.albirich.RealmsAI

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.adapters.FeedAdapter
import com.albirich.RealmsAI.models.FeedEvent
import com.albirich.RealmsAI.models.FeedEventType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class NewsFeedActivity : BaseActivity() {

    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var adminBox: LinearLayout
    private lateinit var adminText: TextView
    private lateinit var createPostBtn: ImageButton

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private var followingList: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_feed)
        setupBottomNav()

        adminBox = findViewById(R.id.adminAnnouncementBox)
        adminText = findViewById(R.id.adminAnnouncementText)
        createPostBtn = findViewById(R.id.btnCreatePost)


        // Single column layout for timelines!
        feedRecyclerView = findViewById(R.id.feedRecyclerView)
        feedRecyclerView.layoutManager = LinearLayoutManager(this)

        var isAdminExpanded = false

        adminBox.setOnClickListener {
            isAdminExpanded = !isAdminExpanded
            if (isAdminExpanded) {
                adminText.maxLines = Integer.MAX_VALUE // Expands completely
            } else {
                adminText.maxLines = 3 // Collapses back down
            }
        }

        createPostBtn.setOnClickListener {
            // 1. Build the input box dynamically
            val inputContainer = FrameLayout(this)
            val editText = EditText(this).apply {
                hint = "What's on your mind?"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
                maxLines = 8
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(50, 20, 50, 20)
                }
            }
            inputContainer.addView(editText)

            // 2. Show the Dialog
            AlertDialog.Builder(this)
                .setTitle("Create Post")
                .setView(inputContainer)
                .setPositiveButton("Post") { _, _ ->
                    val content = editText.text.toString().trim()

                    if (content.length > 500) {
                        Toast.makeText(this, "Post is too long! (Max 500 characters)", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    if (content.isNotEmpty()) {
                        submitTextPost(content)
                    } else {
                        Toast.makeText(this, "Post cannot be empty.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadUserDataAndFeed()
    }

    private fun submitTextPost(content: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Generate a new document reference so we have a unique ID ready
        val docRef = db.collection("feed_events").document()

        val feedEvent = com.albirich.RealmsAI.models.FeedEvent(
            id = docRef.id, // Or however your data class generates its UUID
            authorId = currentUserId,
            type = com.albirich.RealmsAI.models.FeedEventType.TEXT_POST, // Standard text post
            title = "", // No title needed for a raw text post
            content = content,
            referenceId = "",
            timestamp = null
        )

        docRef.set(feedEvent)
            .addOnSuccessListener {
                Toast.makeText(this, "Posted successfully!", Toast.LENGTH_SHORT).show()
                // If you have a function to refresh the feed (like fetching events again), call it here!
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to post: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserDataAndFeed() {
        val userId = currentUserId ?: return

        // 1. Get MY following list so I know whose posts to show
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                followingList = (doc.get("following") as? List<String>) ?: emptyList()

                // 2. Now that we know who we follow, fetch the global feed!
                fetchGlobalFeed()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load network.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchGlobalFeed() {
        db.collection("feed_events")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100) // Pull the latest 100 global posts
            .get()
            .addOnSuccessListener { snap ->
                val allEvents = snap.documents.mapNotNull { it.toObject(FeedEvent::class.java)?.copy(id = it.id) }

                // 1. Check for the most recent Pinned Admin Announcement
                val latestAdminPost = allEvents.firstOrNull { it.type == FeedEventType.ADMIN_ANNOUNCEMENT }
                if (latestAdminPost != null) {
                    adminBox.visibility = View.VISIBLE
                    adminText.text = latestAdminPost.content
                } else {
                    adminBox.visibility = View.GONE
                }

                // 2. Filter the timeline for people I follow (plus any regular admin posts)
                val myTimeline = allEvents.filter { event ->
                    event.authorId in followingList || event.authorId == "ADMIN" || event.authorId == currentUserId
                }

                feedRecyclerView.adapter = FeedAdapter(this@NewsFeedActivity, myTimeline)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load timeline.", Toast.LENGTH_SHORT).show()
            }
    }
}