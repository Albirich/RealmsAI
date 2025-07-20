package com.example.RealmsAI

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.DirectMessage
import com.example.RealmsAI.models.InviteProfile
import com.example.RealmsAI.models.MessageStatus
import com.example.RealmsAI.models.MessageType
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.example.RealmsAI.models.SessionProfile
import com.example.RealmsAI.models.SessionUser
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private var messages: List<DirectMessage>,
    private val onClick: (DirectMessage) -> Unit,
    private val onLongClick: (DirectMessage) -> Unit = {}
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fromText: TextView = itemView.findViewById(R.id.messageFrom)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val timeText: TextView = itemView.findViewById(R.id.messageTime)
        val statusText: TextView = itemView.findViewById(R.id.messageStatus)
        val acceptButton: Button = itemView.findViewById(R.id.acceptButton)
        val declineButton: Button = itemView.findViewById(R.id.declineButton)


        fun bind(msg: DirectMessage) {
            val message = messages[position]
            fromText.text = "From: Loading..."
            messageText.text = msg.text
            timeText.text = formatTimestamp(msg.timestamp)
            statusText.text = when (msg.status) {
                MessageStatus.OPENED -> "Opened"
                MessageStatus.UNOPENED -> "New"
            }
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(msg.from).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: doc.getString("handle") ?: msg.from
                    fromText.text = "From: $name"
                }
                .addOnFailureListener {
                    fromText.text = "From: (unknown)"
                }
            if (message.type == MessageType.FRIEND_REQUEST) {
                acceptButton.visibility = View.VISIBLE
                declineButton.visibility = View.VISIBLE
                acceptButton.setOnClickListener {
                    acceptFriendRequest(msg, itemView.context)
                }
                declineButton.setOnClickListener {
                    declineFriendRequest(msg, itemView.context)
                }

            } else if (message.type == MessageType.SESSION_INVITE) {
                acceptButton.visibility = View.VISIBLE
                declineButton.visibility = View.VISIBLE
                acceptButton.setOnClickListener {
                    acceptSessionInvite(msg, itemView.context)
                }
                declineButton.setOnClickListener {
                    declineSessionInvite(msg, itemView.context)
                }
            } else {
                acceptButton.visibility = View.GONE
                declineButton.visibility = View.GONE
            }
            itemView.setOnClickListener { onClick(msg) }
            itemView.setOnLongClickListener {
                onLongClick(msg)
                true
            }

        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_direct_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(messages[position])
    }

    private fun acceptFriendRequest(message: DirectMessage, context: Context) {
        val db = FirebaseFirestore.getInstance()
        val fromId = message.from
        val toId = message.to

        // Add each user to other's friends
        val usersRef = db.collection("users")
        val batch = db.batch()

        val fromRef = usersRef.document(fromId)
        val toRef = usersRef.document(toId)

        batch.update(fromRef, "friends", FieldValue.arrayUnion(toId))
        batch.update(toRef, "friends", FieldValue.arrayUnion(fromId))
        batch.update(toRef, "pendingFriends", FieldValue.arrayRemove(fromId))

        // Delete the friend request message
        val messageRef = toRef.collection("messages").document(message.id)
        batch.delete(messageRef)

        batch.commit().addOnSuccessListener {
            Toast.makeText(context, "Friend request accepted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun declineFriendRequest(message: DirectMessage, context: Context) {
        val db = FirebaseFirestore.getInstance()
        val toId = message.to
        val fromId = message.from

        val toRef = db.collection("users").document(toId)
        val messageRef = toRef.collection("messages").document(message.id)

        db.runBatch { batch ->
            batch.update(toRef, "pendingFriends", FieldValue.arrayRemove(fromId))
            batch.delete(messageRef)
        }.addOnSuccessListener {
            Toast.makeText(context, "Friend request declined.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun acceptSessionInvite(message: DirectMessage, context: Context) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sessionId = message.sessionId ?: return

        // 1. Add user to session userList
        db.collection("sessions").document(sessionId).get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                Toast.makeText(context, "Session not found.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val sessionProfile = snap.toObject(SessionProfile::class.java) ?: return@addOnSuccessListener
            db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
                val userName = userDoc.getString("id") ?: "Player"
                val userList = sessionProfile.userList.toMutableList()
                if (!userList.contains(userId)) userList.add(userId)

                val userMap = sessionProfile.userMap.toMutableMap()
                if (!userMap.containsKey(userId)) {
                    userMap[userId] = SessionUser(
                        userId = userId,
                        username = userName, // Or fetch real username
                        personaIds = emptyList(),
                        activeSlotId = null,
                        bubbleColor = "#CCCCCC",
                        textColor = "#000000"
                    )
                }

                db.collection("sessions").document(sessionId)
                    .update(
                        mapOf(
                            "userList" to userList,
                            "userMap" to userMap
                        )
                    )
                    .addOnSuccessListener {
                        Toast.makeText(context, "You joined the session!", Toast.LENGTH_SHORT)
                            .show()
                        // 2. Delete the invite message
                        db.collection("users").document(userId)
                            .collection("messages").document(message.id).delete()
                        db.collection("sessions").document(sessionId).get().addOnSuccessListener { sessionSnap ->
                            val updatedSession = sessionSnap.toObject(SessionProfile::class.java)
                            val inviteProfile = Gson().fromJson(message.inviteProfileJson, InviteProfile::class.java)
                            if (updatedSession != null) {
                                val newInviteProfile = InviteProfile(
                                    sessionId = updatedSession.sessionId,
                                    title = updatedSession.title,
                                    userList = updatedSession.userList,
                                    characterIds = inviteProfile.characterIds,
                                    relationships = updatedSession.relationships,
                                    chatId = updatedSession.chatId,
                                    sessionSummary = updatedSession.sessionSummary,
                                    chatMode = updatedSession.chatMode,
                                    areas = updatedSession.areas,
                                    sfwOnly = updatedSession.sfwOnly,
                                    isBuilding = true,
                                    started = false,
                                    sessionDescription = updatedSession.sessionDescription,
                                    secretDescription = updatedSession.secretDescription
                                )
                                val inviteProfileJson = Gson().toJson(newInviteProfile)
                                // Launch lobby with the up-to-date invite profile!
                                val intent = Intent(context, SessionLandingActivity::class.java)
                                intent.putExtra("INVITE_PROFILE_JSON", inviteProfileJson)
                                intent.putExtra("SESSION_ID", sessionId)
                                context.startActivity(intent)
                                if (context is Activity) context.finish()
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to join session.", Toast.LENGTH_SHORT)
                            .show()
                    }
            }
        }
    }


    private fun declineSessionInvite(message: DirectMessage, context: Context) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // Delete the invite message
        db.collection("users").document(userId)
            .collection("messages").document(message.id).delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Invite declined.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update invite.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getItemCount() = messages.size

    fun updateList(newList: List<DirectMessage>) {
        messages = newList
        notifyDataSetChanged()
    }

    private fun formatTimestamp(ts: Timestamp?): String {
        if (ts == null) return ""
        val date = ts.toDate()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(date)
    }
}
