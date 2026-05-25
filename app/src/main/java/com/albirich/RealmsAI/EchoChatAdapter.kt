import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.R

data class ChatMessage(val sender: String, val text: String)

class EchoChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<EchoChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderTv: TextView = view.findViewById(android.R.id.text1)
        val messageTv: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        // Using a standard Android built-in layout for speed, you can customize this later
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.senderTv.text = msg.sender
        holder.senderTv.setTextColor(if (msg.sender == "Echo") 0xFF4CAF50.toInt() else 0xFF2196F3.toInt())

        holder.messageTv.text = msg.text
        holder.messageTv.setTextColor(0xFFFFFFFF.toInt())
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}