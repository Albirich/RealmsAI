package com.albirich.RealmsAI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.ChatMessage
import com.albirich.RealmsAI.models.ChatProfile
import com.albirich.RealmsAI.models.ScenarioEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.firestore.ListenerRegistration

open class BaseActivity : AppCompatActivity() {
    private var messageBadgeListener: ListenerRegistration? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeImmersive()
    }

    private fun makeImmersive() {
        // Use the new WindowInsetsController approach for Android 11+
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeImmersive()
    }
    protected fun setupBottomNav() {

        // Chats
        findViewById<ImageButton>(R.id.navChats).setOnClickListener {
            startActivity(Intent(this, ChatHubActivity::class.java))
        }
        // Social
        findViewById<ImageButton>(R.id.navSocial).setOnClickListener {
            startActivity(Intent(this, NewsFeedActivity::class.java))
        }
        // Create (hub)
        findViewById<ImageButton>(R.id.navCreate).setOnClickListener {
            startActivity(Intent(this, CreationHubActivity::class.java))
        }
        // History / “Created”
        findViewById<ImageButton>(R.id.navHistory).setOnClickListener {
            startActivity(Intent(this, SessionHubActivity::class.java))
        }
        // Profile
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        observeMessagesBadge()
    }
    private fun observeMessagesBadge() {
        val badge = findViewById<View>(R.id.messagesBadge)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Clean up any old listener if already present
        messageBadgeListener?.remove()

        // Real-time Firestore listener for unopened messages
        messageBadgeListener = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("messages")
            .whereEqualTo("status", "UNOPENED") // Adjust if you use enum/string
            .addSnapshotListener { snap, _ ->
                val hasUnopened = snap?.isEmpty == false
                badge?.visibility = if (hasUnopened) View.VISIBLE else View.GONE
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageBadgeListener?.remove()
    }


    // in BaseActivity.kt
    protected fun loadAllChatPreviews(): List<ChatPreview> {
        val now = System.currentTimeMillis()

        // --- 1) build a lookup of characterId → CharacterProfile ---
        val charProfilesById = loadAllCharacterProfiles()
            .associateBy { it.id }

        val prefs = getSharedPreferences("chats", Context.MODE_PRIVATE)
        return prefs.all.values
            .mapNotNull { it as? String }
            .mapNotNull { json ->
                runCatching { Gson().fromJson(json, ChatProfile::class.java) }
                    .getOrNull()
            }
            .map { profile ->
                val ids = profile.characterIds

                // first slot
                val cp1       = charProfilesById[ids.getOrNull(0)]
                val uri1      = cp1?.avatarUri
                val res1      = cp1?.avatarResId ?: R.drawable.icon_01

                // second slot
                val cp2       = charProfilesById[ids.getOrNull(1)]
                val uri2      = cp2?.avatarUri
                val res2      = cp2?.avatarResId ?: res1

                ChatPreview(
                    id           = profile.id,
                    originalId   = profile.originalId,
                    title        = profile.title,
                    description  = profile.description,
                    avatar1ResId = res1,
                    avatar2ResId = res2,
                    avatar1Uri   = uri1,
                    avatar2Uri   = uri2,
                    rating       = profile.rating,
                    timestamp    = profile.timestamp,
                    author       = profile.author,
                    chatProfile  = profile,
                    rawJson      = Gson().toJson(profile)
                )
            }
    }



    /**
     * Loads every saved CharacterProfile from SharedPreferences.
     */
    protected fun loadAllCharacterProfiles(): List<CharacterProfile> {
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        return prefs.all.values
            .mapNotNull { it as? String }
            .mapNotNull { json ->
                runCatching {
                    Gson().fromJson(json, CharacterProfile::class.java)
                }.getOrNull()
            }
    }

    protected fun loadChatSession(chatId: String): List<ChatMessage> {
        val prefs = getSharedPreferences("sessions", Context.MODE_PRIVATE)
        val raw   = prefs.getString(chatId, null) ?: return emptyList()

        return runCatching {
            val root = JSONObject(raw)
            val arr  = root.getJSONArray("messages")
            val gson = Gson()
            List(arr.length()) { i ->
                gson.fromJson(arr.getJSONObject(i).toString(), ChatMessage::class.java)
            }
        }.getOrDefault(emptyList())
    }

    protected fun saveChatSession(
        chatId: String,
        title: String,
        messages: List<ChatMessage>,
        author: String
    ) {
        val obj = JSONObject().apply {
            put("id",       chatId)
            put("title",    title)
            put("author",   author)
            put("messages", JSONArray(Gson().toJson(messages)))
        }
        getSharedPreferences("sessions", Context.MODE_PRIVATE)
            .edit()
            .putString(chatId, obj.toString())
            .apply()
    }

    protected fun clearChatHistoryFromPrefs(chatId: String) {
        getSharedPreferences("sessions", Context.MODE_PRIVATE)
            .edit()
            .remove(chatId)
            .apply()
    }
}

class EventAdapter(
    val events: MutableList<ScenarioEvent>,
    val onDeleteEvent: (position: Int) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventHolder>() {

    inner class EventHolder(v: View) : RecyclerView.ViewHolder(v) {
        val titleEt = v.findViewById<EditText>(R.id.eventTitleEt)
        val descriptionEt = v.findViewById<EditText>(R.id.eventDescriptionEt)
        val narratorEt = v.findViewById<EditText>(R.id.eventNarratorEt)
        val deleteBtn = v.findViewById<ImageButton>(R.id.deleteEventBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventHolder(v)
    }

    override fun onBindViewHolder(holder: EventHolder, position: Int) {
        val event = events[position]

        // 1. Title
        holder.titleEt.setText(event.title)
        holder.titleEt.doAfterTextChanged { editable ->
            event.title = editable?.toString() ?: ""
        }

        // 2. System Description
        holder.descriptionEt.setText(event.description)
        holder.descriptionEt.doAfterTextChanged { editable ->
            event.description = editable?.toString() ?: ""
        }

        // 3. Narrator Message
        holder.narratorEt.setText(event.narratorMessage)
        holder.narratorEt.doAfterTextChanged { editable ->
            event.narratorMessage = editable?.toString() ?: ""
        }

        // 4. Delete Button
        holder.deleteBtn.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onDeleteEvent(currentPos)
            }
        }
    }

    override fun getItemCount(): Int = events.size
}