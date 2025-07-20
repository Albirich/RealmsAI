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
import com.example.RealmsAI.R
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.SessionUser
import com.example.RealmsAI.models.SlotProfile
import kotlin.collections.addAll
import kotlin.text.clear

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onNewMessage: (position: Int) -> Unit,
    private val slotProfiles: List<SlotProfile>,
    private val sessionUsers: List<SessionUser>,
    private val currentUserId: String,
    private val onEditMessage: (editedMessage: ChatMessage, position: Int) -> Unit,
    private val onDeleteMessages: (fromPosition: Int) -> Unit,
    private val isMultiplayer: Boolean


) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {


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
            isBot -> slotProfiles.find { it.slotId == msg.senderId }?.name ?: "Bot"
            msg.senderId == "narrator" -> "Narrator"
            msg.senderId == "Narrator" -> "Narrator"
            else -> sessionUsers.find { it.userId == msg.senderId }?.username ?: "Player"
        }
        holder.messageTextView.text = "$senderLabel: ${msg.text}"

        // Set colors
        val style = when {
            isBot -> slotProfiles.find { it.slotId == msg.senderId }
                ?.let { MessageStyle(safeParseColor(it.bubbleColor, 0xFFCCCCCC.toInt()), safeParseColor(it.textColor, 0xFF000000.toInt())) }
            msg.senderId == "narrator" -> MessageStyle(0xFFEEEEEE.toInt(), 0xFF000000.toInt())
            else -> sessionUsers.find { it.userId == msg.senderId }
                ?.let { MessageStyle(safeParseColor(it.bubbleColor, 0xFF99CCFF.toInt()), safeParseColor(it.textColor, 0xFF000000.toInt())) }
        } ?: MessageStyle(0xFFCCCCCC.toInt(), 0xFF000000.toInt())

        holder.messageContainer.setBackgroundColor(style.backgroundColor)
        holder.messageTextView.setTextColor(style.textColor)

        // Align player messages right, bots left
        val isPlayerMessage = !isBot && msg.senderId == currentUserId
        (holder.messageContainer.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
            ?.apply { horizontalBias = if (isPlayerMessage) 1f else 0f }
            ?.also { holder.messageContainer.layoutParams = it }

        // Long-press: edit/delete dialog
        holder.itemView.setOnLongClickListener {
            if (isMultiplayer) {
                // Do nothing, block popup entirely in multiplayer
                return@setOnLongClickListener true
            }
            val ctx = holder.itemView.context
            val edit = EditText(ctx).apply { setText(msg.text) }
            AlertDialog.Builder(ctx)
                .setTitle("Edit Message")
                .setView(edit)
                .setPositiveButton("Resend + Delete following") { _, _ ->
                    val newText = edit.text.toString()
                    val newMsg = msg.copy(text = newText)
                    messages[position] = newMsg
                    notifyItemChanged(position)

                    // Notify MainActivity to handle saving, deletion after this message, and AI restart
                    onEditMessage(newMsg, position)
                }
                .setNeutralButton("Delete this + following") { _, _ ->
                    onDeleteMessages(position)
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
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
}
