package com.example.RealmsAI

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatMessage

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    /**
     * Called with the new item’s position whenever addMessage() is used.
     * In MainActivity you’ll hook this up to RecyclerView.smoothScrollToPosition().
     */
    private val onNewMessage: (position: Int) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    data class MessageStyle(val backgroundColor: Int, val textColor: Int)

    private fun getSenderStyle(sender: String): MessageStyle {
        return when (sender) {
            "You"   -> MessageStyle(0xaa0000FF.toInt(), 0xaaFFFFFF.toInt())
            "Bot 1" -> MessageStyle(0xaaFFA500.toInt(), 0xaa008000.toInt())
            "Bot 2" -> MessageStyle(0xaa800080.toInt(), 0xaaFFC0CB.toInt())
            else    -> MessageStyle(0xaaCCCCCC.toInt(), 0xaa000000.toInt())
        }
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

        // alignment & colors
        val style = getSenderStyle(msg.sender)
        // holder.messageContainer is your bubble’s parent layout
        holder.messageContainer.setBackgroundColor(style.backgroundColor)
        // and you already had:
        holder.messageTextView.setTextColor(style.textColor)
        (holder.messageContainer.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
            ?.apply { horizontalBias = if (msg.sender == "You") 1f else 0f }
            ?.also { holder.messageContainer.layoutParams = it }

        // long-press to edit/delete
        holder.itemView.setOnLongClickListener {
            val ctx = holder.itemView.context
            val edit = EditText(ctx).apply { setText(msg.messageText) }
            AlertDialog.Builder(ctx)
                .setTitle("Edit Message")
                .setView(edit)
                .setPositiveButton("Save") { _, _ ->
                    msg.messageText = edit.text.toString()
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

    /** Add a message, notify the insertion, then fire your scroll hook. */
    fun addMessage(msg: ChatMessage) {
        messages += msg
        val pos = messages.size - 1
        notifyItemInserted(pos)
        onNewMessage(pos)
    }

    /** Clear both list and view. */
    fun clearMessages() {
        val count = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, count)
    }


    fun getMessages(): List<ChatMessage> = messages.toList()
}
