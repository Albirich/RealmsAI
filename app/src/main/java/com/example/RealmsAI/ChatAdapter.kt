package com.example.RealmsAI

import android.app.AlertDialog
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.PersonaProfile
import com.example.RealmsAI.models.SlotInfo

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onNewMessage: (position: Int) -> Unit,
    private val slotInfoList: List<SlotInfo>,
    private val personaProfiles: List<PersonaProfile>,
    private val userPersonaName: String
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    data class MessageStyle(val backgroundColor: Int, val textColor: Int)

    private fun getSenderColors(sender: String): MessageStyle {
        slotInfoList.find { it.name == sender }?.let {
            return MessageStyle(
                safeParseColor(it.bubbleColor, 0xFFCCCCCC.toInt()),
                safeParseColor(it.textColor, 0xFF000000.toInt())
            )
        }
        personaProfiles.find { it.name == sender }?.let {
            return MessageStyle(
                safeParseColor(it.bubbleColor, 0xFFCCCCCC.toInt()),
                safeParseColor(it.textColor, 0xFF000000.toInt())
            )
        }
        // Default: gray bubble, black text
        return MessageStyle(0xFFCCCCCC.toInt(), 0xFF000000.toInt())
    }

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView      = view.findViewById(R.id.messageTextView)
        val messageContainer: LinearLayout = view.findViewById(R.id.messageContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(v)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        holder.messageTextView.text = "${msg.sender}: ${msg.messageText}"

        // Prefer explicit color from the message, fallback to profile/defaults
        val style = if (msg.bubbleBackgroundColor != 0xFFFFFFFF.toInt() || msg.bubbleTextColor != 0xFF000000.toInt()) {
            // If bubble/text color differ from hardcoded default, use them
            MessageStyle(msg.bubbleBackgroundColor, msg.bubbleTextColor)
        } else {
            getSenderColors(msg.sender)
        }

        holder.messageContainer.setBackgroundColor(style.backgroundColor)
        holder.messageTextView.setTextColor(style.textColor)

        // Align user persona right, bots/NPCs left
        (holder.messageContainer.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
            ?.apply { horizontalBias = if (msg.sender == userPersonaName) 1f else 0f }
            ?.also { holder.messageContainer.layoutParams = it }

        // Long-press: edit/delete
        holder.itemView.setOnLongClickListener {
            val ctx = holder.itemView.context
            val edit = EditText(ctx).apply { setText(msg.messageText) }
            AlertDialog.Builder(ctx)
                .setTitle("Edit Message")
                .setView(edit)
                .setPositiveButton("Save") { _, _ ->
                    val newMsg = msg.copy(messageText = edit.text.toString())
                    messages[position] = newMsg
                    notifyItemChanged(position)
                }
                .setNeutralButton("Delete this + following") { _, _ ->
                    for (i in messages.size - 1 downTo position) {
                        messages.removeAt(i)
                        notifyItemRemoved(i)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
    }

    fun safeParseColor(colorString: String?, default: Int): Int {
        return try {
            if (colorString.isNullOrBlank()) default else Color.parseColor(colorString)
        } catch (e: Exception) {
            default
        }
    }

    /** Add a message, notify the insertion, then fire your scroll hook. */
    fun addMessage(newMsg: ChatMessage) {
        Log.d("ChatAdapter", "Trying to add message ID: ${newMsg.id}")
        messages.add(newMsg)
        Log.d("ChatAdapter", "addMessage: Added message '${newMsg.messageText.take(30)}...' at position ${messages.size - 1}")
        notifyItemInserted(messages.size - 1)
        onNewMessage(messages.size - 1)
    }

    /** Clear both list and view. */
    fun clearMessages() {
        val count = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun getMessages(): List<ChatMessage> = messages.toList()
}
