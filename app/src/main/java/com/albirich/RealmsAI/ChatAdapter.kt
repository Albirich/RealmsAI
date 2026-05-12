import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.models.ChatMessage
import com.albirich.RealmsAI.models.SessionUser
import com.albirich.RealmsAI.models.SlotProfile

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onNewMessage: (position: Int) -> Unit,
    private var slotProfiles: List<SlotProfile>,
    private val sessionUsers: List<SessionUser>,
    private val currentUserId: String,
    private val mode: AdapterMode = AdapterMode.CHAT,
    private val onReRoll: ((ChatMessage, Int) -> Unit)? = null,
    private val onEditMessage: (editedMessage: ChatMessage, position: Int) -> Unit,
    private val onInlineEditMessage: (editedMessage: ChatMessage, position: Int) -> Unit,
    private val onPinMessage: (messageToPin: ChatMessage) -> Unit,
    private val onDeleteMessages: (fromPosition: Int) -> Unit,
    private val isMultiplayer: Boolean,
    private var isColorBlindMode: Boolean = false

) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>()
{
    data class MessageStyle(val backgroundColor: Int, val textColor: Int)
    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.messageTextView)
        val messageContainer: LinearLayout = view.findViewById(R.id.messageContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(v)
    }
    fun getMessageAt(position: Int): ChatMessage = messages[position]
    override fun getItemCount(): Int = messages.size

    fun setMessages(messages: List<ChatMessage>) {
        this.messages.clear()
        this.messages.addAll(messages)
        notifyDataSetChanged()
    }
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]

        if (!msg.visibility) {
            holder.itemView.visibility = View.GONE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Determine sender type and label
        val isBot = slotProfiles.any { it.slotId == msg.senderId }
        val senderLabel = when {
            !msg.displayName.isNullOrBlank() -> msg.displayName
            isBot -> slotProfiles.find { it.slotId == msg.senderId }?.name ?: "Bot"
            msg.senderId == "narrator" || msg.senderId == "Narrator" -> "Narrator"
            else -> sessionUsers.find { it.userId == msg.senderId }?.username ?: "Player"
        }
        holder.messageTextView.text = "$senderLabel: ${msg.text}"

        // --- STYLING LOGIC ---
        // 1. Reset text style to normal first (crucial for RecyclerView recycling)
        holder.messageTextView.setTypeface(null, android.graphics.Typeface.NORMAL)

        if (msg.messageType == "godMsg") {
            // 2. GOD MODE PROMPT STYLING
            // Makes it look like a GM prompt (Dark gold background, bright yellow text, bold italic)
            holder.messageContainer.setBackgroundColor(Color.parseColor("#3A2A00"))
            holder.messageTextView.setTextColor(Color.parseColor("#FFD700"))
            holder.messageTextView.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC)
        } else {
            // 3. STANDARD CHAT STYLING
            val style = if (isColorBlindMode) {
                // HIGH CONTRAST MODE: Pure Black and White for maximum readability
                MessageStyle(Color.WHITE, Color.BLACK)
            } else {
                when {
                    isBot -> slotProfiles.find { it.slotId == msg.senderId }
                        ?.let {
                            MessageStyle(
                                safeParseColor(it.bubbleColor, 0xFFCCCCCC.toInt()),
                                safeParseColor(it.textColor, 0xFF000000.toInt())
                            )
                        }
                    msg.senderId == "narrator" || msg.senderId == "Narrator" -> MessageStyle(0xFFEEEEEE.toInt(), 0xFF000000.toInt())
                    else -> sessionUsers.find { it.userId == msg.senderId }
                        ?.let {
                            MessageStyle(
                                safeParseColor(it.bubbleColor, 0xFF99CCFF.toInt()),
                                safeParseColor(it.textColor, 0xFF000000.toInt())
                            )
                        }
                } ?: MessageStyle(0xFFCCCCCC.toInt(), 0xFF000000.toInt())
            }

            holder.messageContainer.setBackgroundColor(style.backgroundColor)
            holder.messageTextView.setTextColor(style.textColor)
        }

        // Align player messages right, bots left
        val isPlayerMessage = !isBot && msg.senderId == currentUserId
        (holder.messageContainer.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
            ?.apply { horizontalBias = if (isPlayerMessage) 1f else 0f }
            ?.also { holder.messageContainer.layoutParams = it }

        // Long-press: edit/delete dialog
        holder.itemView.setOnLongClickListener {
            when (mode) {
                AdapterMode.CHAT -> {
                    if (isMultiplayer) {
                        // Do nothing, block popup entirely in multiplayer
                        return@setOnLongClickListener true
                    }

                    val ctx = holder.itemView.context
                    val options = arrayOf("Edit (Inline)", "Rewind & Resend", "Pin Message", "Delete (This & Following)")

                    AlertDialog.Builder(ctx)
                        .setTitle("Message Options")
                        .setItems(options) { _, which ->
                            when (which) {
                                0 -> {
                                    // 1. INLINE EDIT: Changes text without rewinding AI
                                    val edit = EditText(ctx).apply { setText(msg.text) }
                                    AlertDialog.Builder(ctx)
                                        .setTitle("Edit Message (Inline)")
                                        .setView(edit)
                                        .setPositiveButton("Save") { _, _ ->
                                            val newText = edit.text.toString()
                                            val newMsg = msg.copy(text = newText)
                                            messages[position] = newMsg
                                            notifyItemChanged(position)
                                            onInlineEditMessage(newMsg, position)
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                                1 -> {
                                    // 2. REWIND & RESEND: Edits, deletes following, triggers AI
                                    val edit = EditText(ctx).apply { setText(msg.text) }
                                    AlertDialog.Builder(ctx)
                                        .setTitle("Rewind & Resend")
                                        .setView(edit)
                                        .setPositiveButton("Send") { _, _ ->
                                            val newText = edit.text.toString()
                                            val newMsg = msg.copy(text = newText)
                                            messages[position] = newMsg
                                            notifyItemChanged(position)
                                            onEditMessage(newMsg, position)
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                                2 -> {
                                    // 3. PIN MESSAGE: Adds to SessionProfile pinnedMessages
                                    onPinMessage(msg)
                                }
                                3 -> {
                                    // 4. DELETE: Deletes this and all following
                                    AlertDialog.Builder(ctx)
                                        .setTitle("Delete Messages")
                                        .setMessage("Are you sure you want to delete this message and all messages after it?")
                                        .setPositiveButton("Delete") { _, _ ->
                                            onDeleteMessages(position)
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                            }
                        }
                        .show()
                    true
                }

                AdapterMode.ROLL_HISTORY -> {
                    // Added "Pin Message" to the array!
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Roll Options")
                        .setItems(arrayOf("Re-Roll", "Pin Message", "Delete")) { _, which ->
                            when (which) {
                                0 -> onReRoll?.invoke(msg, position)
                                1 -> onPinMessage(msg) // <-- NEW!
                                2 -> onDeleteMessages(position)
                            }
                        }
                        .show()
                    true
                }
            }

        }
    }

    fun insertMessageAt(position: Int, message: ChatMessage) {
        messages.add(position, message)
        notifyItemInserted(position)
    }

    fun safeParseColor(colorString: String?, default: Int): Int {
        return try {
            if (colorString.isNullOrBlank()) default else Color.parseColor(colorString)
        } catch (e: Exception) {
            default
        }
    }
    fun removeMessagesFrom(position: Int) {
        val count = messages.size - position
        if (count <= 0) return
        for (i in messages.size - 1 downTo position) {
            messages.removeAt(i)
        }
        notifyItemRangeRemoved(position, count)
    }
    fun addMessage(newMsg: ChatMessage) {
        // Prevent duplicates (by unique ID)
        if (messages.any { it.id == newMsg.id }) return
        messages.add(newMsg)
        notifyItemInserted(messages.size - 1)
        onNewMessage(messages.size - 1)
    }

    fun clearMessages() {
        val count = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun getMessages(): List<ChatMessage> = messages.toList()
    enum class AdapterMode {
        CHAT, ROLL_HISTORY
    }

    fun updateMessageAt(position: Int, message: ChatMessage) {
        messages[position] = message
        notifyItemChanged(position)
    }

    fun setColorBlindMode(enabled: Boolean) {
        this.isColorBlindMode = enabled
        notifyDataSetChanged() // Refresh all messages with new colors
    }

    fun updateSlotProfiles(newProfiles: List<SlotProfile>) {
        this.slotProfiles = newProfiles
        notifyDataSetChanged() // Instantly redraws the chat with new colors!
    }
}
