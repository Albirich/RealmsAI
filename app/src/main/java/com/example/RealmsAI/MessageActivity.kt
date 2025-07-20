package com.example.RealmsAI

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.DirectMessage
import com.example.RealmsAI.models.MessageStatus
import com.example.RealmsAI.models.MessageType
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MessagesActivity : BaseActivity() {

    private lateinit var newMessageButton: Button
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter

    private val userId get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)
        setupBottomNav()

        newMessageButton = findViewById(R.id.newMessageButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messagesRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(
            messages = listOf(),
            onClick = { msg -> showMessageDialog(msg) },
            onLongClick = { msg -> showDeleteDialog(msg) }
        )
        messagesRecyclerView.adapter = adapter

        newMessageButton.setOnClickListener {
            showNewMessageDialog()
        }

        // Load your messages here!
        loadMessages(userId) { messages ->
            adapter.updateList(messages)
        }
    }

    private fun showMessageDialog(msg: DirectMessage) {
        val dialogView = layoutInflater.inflate(R.layout.message_detail_dialog, null)
        val messageDetailText = dialogView.findViewById<TextView>(R.id.messageDetailText)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        messageDetailText.text = msg.text

        val replyButton = dialogView.findViewById<Button>(R.id.replyButton)
        val deleteButton = dialogView.findViewById<Button>(R.id.deleteButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        db.collection("users").document(currentUserId).collection("messages").document(msg.id).update("status", MessageStatus.OPENED.name)

        replyButton.setOnClickListener {
            dialog.dismiss()
            showReplyDialog(msg) // Show reply dialog, pre-filling recipient
        }

        deleteButton.setOnClickListener {
            dialog.dismiss()
            showDeleteDialog(msg)
        }

        dialog.show()
    }

    private fun showNewMessageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.new_message_dialog, null)
        val friendsSpinner = dialogView.findViewById<Spinner>(R.id.friendsSpinner)
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)
        val sendButton = dialogView.findViewById<Button>(R.id.sendButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Load current user's friends for the spinner
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val friends = doc.get("friends") as? List<String> ?: emptyList()
                if (friends.isEmpty()) {
                    Toast.makeText(this, "No friends to message. :(", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Lookup each friend's handle or name for display
                db.collection("users").whereIn(FieldPath.documentId(), friends)
                    .get()
                    .addOnSuccessListener { snap ->
                        val friendIdToName = mutableMapOf<String, String>()
                        val friendNames = snap.documents.map { userDoc ->
                            val name = userDoc.getString("name") ?: userDoc.getString("handle") ?: userDoc.id
                            friendIdToName[userDoc.id] = name
                            name
                        }
                        // Map spinner position to friend ID
                        val friendIds = snap.documents.map { it.id }

                        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, friendNames)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        friendsSpinner.adapter = adapter

                        val dialog = AlertDialog.Builder(this)
                            .setView(dialogView)
                            .show()

                        sendButton.setOnClickListener {
                            val selectedPosition = friendsSpinner.selectedItemPosition
                            if (selectedPosition == AdapterView.INVALID_POSITION) {
                                Toast.makeText(this, "Select a friend.", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            val toId = friendIds[selectedPosition]
                            val text = messageEditText.text.toString().trim()
                            if (text.isBlank()) {
                                Toast.makeText(this, "Enter a message.", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            // Build DirectMessage
                            val messageId = db.collection("users").document(toId).collection("messages").document().id
                            val directMessage = DirectMessage(
                                id = messageId,
                                from = currentUserId,
                                to = toId,
                                text = text,
                                timestamp = Timestamp.now(),
                                status = MessageStatus.UNOPENED,
                                type = MessageType.DIRECT
                            )
                            db.collection("users").document(toId)
                                .collection("messages")
                                .document(messageId)
                                .set(directMessage)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Message sent!", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        cancelButton.setOnClickListener { dialog.dismiss() }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load friends.", Toast.LENGTH_SHORT).show()
                    }
            }
    }


    private fun showDeleteDialog(msg: DirectMessage) {
        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                deleteMessage(msg)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(msg: DirectMessage) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .collection("messages").document(msg.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                loadMessages(userId) { messages ->
                    adapter.updateList(messages)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showReplyDialog(msg: DirectMessage) {
        val dialogView = layoutInflater.inflate(R.layout.new_message_dialog, null)
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)
        val sendButton = dialogView.findViewById<Button>(R.id.sendButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val friendsSpinner = dialogView.findViewById<Spinner>(R.id.friendsSpinner)
        friendsSpinner.visibility = View.GONE

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        sendButton.setOnClickListener {
            val replyText = messageEditText.text.toString().trim()
            if (replyText.isNotEmpty()) {
                sendMessage(msg.from, replyText, userId)
                dialog.dismiss()
            }
        }

        cancelButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    fun sendMessage(toUserId: String, text: String, fromUserId: String) {
        val db = FirebaseFirestore.getInstance()
        val message = DirectMessage(
            id = "", // Firestore can auto-generate
            from = fromUserId,
            to = toUserId,
            text = text,
            timestamp = Timestamp.now(),
            status = MessageStatus.UNOPENED
        )
        db.collection("users").document(toUserId)
            .collection("messages")
            .add(message) // or .document(id).set(message) if you want to control id
    }

    private fun loadMessages(userId: String, onResult: (List<DirectMessage>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val messages = snap.documents.mapNotNull { it.toObject(DirectMessage::class.java)?.copy(id = it.id) }
                onResult(messages)
            }
    }
}
