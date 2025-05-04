package com.example.RealmsAI

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {



    data class MessageStyle(
        val backgroundColor: Int,
        val textColor: Int,
    )

    fun getSenderStyle(sender: String): MessageStyle {
        return when (sender) {
            "User" -> MessageStyle(0xaa0000FF.toInt(), 0xaaFFFFFF.toInt())
            "Bot 1" -> MessageStyle(0xaaFFA500.toInt(), 0xaa008000.toInt())
            "Bot 2" -> MessageStyle(0xaa800080.toInt(), 0xaaFFC0CB.toInt())
            else -> MessageStyle(0xaaCCCCCC.toInt(), 0xaa000000.toInt())
        }
    }
    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatMessage = messages[position]
        holder.messageTextView.text = "${chatMessage.sender}: ${chatMessage.messageText}"
        val style = getSenderStyle(chatMessage.sender)
        holder.messageTextView.setTextColor(style.textColor)
        val messageContainer = holder.itemView.findViewById<LinearLayout>(R.id.messageContainer)

        val params = messageContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (chatMessage.sender == "You") {
            params.horizontalBias = 1f
        } else {
            params.horizontalBias = 0f
        }
        messageContainer.layoutParams = params

        val drawable = holder.itemView.context.getDrawable(R.drawable.bubble_shape)?.mutate() as? GradientDrawable
        drawable?.setColor(style.backgroundColor)
        holder.messageTextView.background = drawable

        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context
            val editText = EditText(context)
            editText.setText(chatMessage.messageText)
            AlertDialog.Builder(context)
                .setTitle("Edit Message")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    chatMessage.messageText = editText.text.toString()
                    notifyItemChanged(holder.adapterPosition)
                }
                .setNeutralButton("Delete this and following") { _, _ ->
                    val pos = holder.adapterPosition
                    for (i in messages.size - 1 downTo pos) {
                        messages.removeAt(i)
                        notifyItemRemoved(i)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
        // trigger save
        onNewMessage?.invoke()
    }

    fun getMessages(): List<ChatMessage> = messages

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    companion object {
        fun clearMessages() {
            TODO("Not yet implemented")
        }
    }

}
