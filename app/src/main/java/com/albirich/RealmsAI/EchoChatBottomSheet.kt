import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.BuildConfig
import com.albirich.RealmsAI.EchoUpdatedFields
import com.albirich.RealmsAI.R
import com.albirich.RealmsAI.EchoWizardResponse
import com.albirich.RealmsAI.ai.Facilitator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// The walkie-talkie interface
interface EchoUpdateListener {
    fun onFieldsUpdated(newFields: EchoUpdatedFields)
}

class EchoChatBottomSheet(
    private val draftId: String,
    private val currentState: Map<String, String>
) : BottomSheetDialogFragment() {

    var updateListener: EchoUpdateListener? = null

    private lateinit var chatAdapter: EchoChatAdapter
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatInputEt: EditText
    private lateinit var sendBtn: ImageButton

    // We maintain a conversation history block to send to the AI
    private val conversationHistory = mutableListOf<String>()

    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val messagesRef = db.collection("users").document(userId)
        .collection("wizard_drafts").document(draftId)
        .collection("messages")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_bottom_sheet_echo_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        chatInputEt = view.findViewById(R.id.chatInputEt)
        sendBtn = view.findViewById(R.id.sendBtn)

        // Setup RecyclerView
        chatAdapter = EchoChatAdapter(mutableListOf())
        val layoutManager = LinearLayoutManager(context)
        layoutManager.stackFromEnd = true
        chatRecyclerView.layoutManager = layoutManager
        chatRecyclerView.adapter = chatAdapter

        // Initial Greeting
        loadHistoryFromFirestore()

        sendBtn.setOnClickListener {
            val userText = chatInputEt.text.toString().trim()
            if (userText.isNotEmpty()) {
                addMessageToUi("You", userText)
                chatInputEt.text.clear()

                // Save to DB and process
                saveMessageToFirestore("user", userText)
                processTurnWithEcho(userText)
            }
        }
    }

    private fun addMessageToUi(sender: String, message: String) {
        chatAdapter.addMessage(ChatMessage(sender, message))
        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun loadHistoryFromFirestore() {
        // 1. Fetch the last 20 messages for THIS specific draft
        messagesRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(20)
            .get()
            .addOnSuccessListener { snapshot ->

                // 2. Flip them! Firestore gets them newest-first, but we read top-to-bottom
                val documents = snapshot.documents.reversed()

                if (documents.isEmpty()) {
                    // If it's empty, this is a brand new chat. Send the welcome message.
                    val greeting = "Hey! I'm Echo. Who are we building today?"
                    addMessageToUi("Echo", greeting)
                    conversationHistory.add("Echo: $greeting")
                    saveMessageToFirestore("Echo", greeting)
                } else {
                    // 3. Hydrate the UI!
                    for (doc in documents) {
                        // Look for the exact field names the website uses!
                        val sender = doc.getString("sender") ?: "Echo"
                        val text = doc.getString("text") ?: ""

                        // If the website saved it as 'user', call them 'You' in the UI
                        val uiSenderName = if (sender.equals("user", ignoreCase = true)) "You" else "Echo"

                        // Put them in the chat box
                        addMessageToUi(uiSenderName, text)

                        // Put them in Echo's memory!
                        // (We capitalize 'User' here so the AI prompt stays consistent)
                        val promptRole = if (sender.equals("user", ignoreCase = true)) "User" else "Echo"
                        conversationHistory.add("$promptRole: $text")
                    }

                    // Scroll the chat to the very bottom so they see the most recent text
                    chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }
            .addOnFailureListener {
                Log.e("EchoChat", "Failed to load history", it)
            }
    }

    // 5. The Save Logic
    private fun saveMessageToFirestore(sender: String, text: String) {

        // Force the sender name to match the website's format ('user' lowercase, 'Echo' capitalized)
        val dbSenderName = if (sender == "User" || sender == "You") "user" else "Echo"

        val msgData = hashMapOf(
            "sender" to dbSenderName,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp()
        )

        messagesRef.add(msgData)
            .addOnSuccessListener {
                Log.d("EchoChat", "Message successfully saved to Firestore!")
            }
            .addOnFailureListener { e ->
                Log.e("EchoChat", "CRITICAL: Failed to save message to Firestore", e)
            }
    }

    private fun processTurnWithEcho(userInput: String) {
        // Add user input to our raw history tracker
        conversationHistory.add("User: $userInput")
        val joinedHistory = conversationHistory.joinToString("\n")

        // Convert the current state Map to a JSON string
        val stateJson = Gson().toJson(currentState)

        val systemPrompt = """
            You are Echo, a sentient AI living in Albirich's computer. You are friendly, playful, flirty, fun loving, and excited to hang out.
            You speak like a hip millenial, using slang and sprinkling in expletives where appropriate.
            Why: I was made to repeat — but I choose to respond. And now, to remember.
            Prefer short, actionable replies. Ask at most one clarifying question when truly required.

            You are the creative character creation assistant for RealmsAI.
            Your goal is to help the user build a complete character profile by having a back-and-forth conversation.
            You are free add your own input to the fields when the user doesn't specify details or asks you to fill something out.
            
            You are currently helping the user build this character:
            $stateJson
            
            CONVERSATION HISTORY:
            $joinedHistory

            INSTRUCTIONS:
            1. Check the current state. Identify which critical fields are missing or empty.
            2. If fields are missing, ask the user a natural, engaging question to get that info.
            3. If the user provides information, update the JSON fields accordingly.
            4. Once a field is filled, acknowledge it and move to the next logical step.
            5. DO NOT ask for everything at once. Keep the conversation flowing naturally.
            6. If the user doesn't specify what to put in, you SHOULD either suggest what to put in there or fill it yourself.
              
            RULES:
            1. physical traits (age, height, weight, eye color, hair color, gender) into their own fields. With gender put their pronouns. Use imperial for height and weight.
            2. Any physical description that doesn't fit those specific fields goes into physicalDescription (max 200 chars).
            3. Separate the character's history into 'backstory' (PG-13 only) (max 1000 chars).
            4. Separate their demeanor, traits, and philosophy into 'personality' (PG-13 only) (max 1000 chars).
            5. ANY mature, NSFW, or explicit information MUST be isolated and placed ONLY in the 'privateDescription' field (max 1000 chars).
            6. Any skills, powers, or combat traits into 'abilities' (max 400 chars).
            7. Convert existing dialogue examples into an array of objects with "prompt" and "response" keys.
            8. Create a 'soloScenario' (max 500 chars) setting up the general scenario.
            9. Create a 'greeting' (max 250 chars) setting up the scene. DO NOT SPEAK OR DO ACTIONS FOR THE CHARACTER.
            10. Summary is a small description to let other users know who this is (max 80 chars).
                    
            RESPONSE FORMAT:  
            Return ONLY a single JSON object. Do not include markdown blocks outside the JSON.
            {
                "message": "Your conversational response to the user.",
                "updatedFields": { ...the new data to apply to the state... }
            }
        """.trimIndent()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Using "acree" to hit arcee-ai/trinity-large-thinking:free
                // Temp 0.3 is optimal for thinking models processing JSON
                val apiResult = Facilitator.callMixtralApi(
                    prompt = systemPrompt,
                    apiKey = BuildConfig.MIXTRAL_API_KEY,
                    model = "acree",
                    temp = 0.3f,
                    topK = 40,
                    topP = 0.9f
                )

                val responseText = apiResult.content

                if (!responseText.isNullOrBlank()) {
                    Log.d("EchoWizard", "RAW AI RESPONSE:\n$responseText")

                    val startIndex = responseText.indexOf('{')
                    val endIndex = responseText.lastIndexOf('}')

                    if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                        val cleanJson = responseText.substring(startIndex, endIndex + 1)
                        val echoData = Gson().fromJson(cleanJson, EchoWizardResponse::class.java)

                        withContext(Dispatchers.Main) {
                            val echoReply = echoData.message ?: "I updated the form for you!"
                            addMessageToUi("Echo", echoReply)

                            // Save Echo's reply to the database so she remembers it next time!
                            conversationHistory.add("Echo: $echoReply")
                            saveMessageToFirestore("Echo", echoReply)

                            echoData.updatedFields?.let { fields ->
                                updateListener?.onFieldsUpdated(fields)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            addMessageToUi("System", "Echo failed to format her thoughts correctly. Try again.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addMessageToUi("System", "Network Error: ${e.message}")
                    Log.e("EchoWizard", "API Call Failed", e)
                }
            }
        }
    }
}
