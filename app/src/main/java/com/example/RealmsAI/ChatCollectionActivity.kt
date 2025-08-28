package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.adapters.CollectionAdapter.CharacterRowAdapter
import com.example.RealmsAI.models.CharacterCollection
import com.example.RealmsAI.models.CharacterPreview
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ModeSettings
import com.example.RealmsAI.models.SlotProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class ChatCollectionActivity : AppCompatActivity() {

    private val selectedCharacters = mutableListOf<CharacterProfile>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CharacterPreviewAdapter
    private val slotRoster = mutableListOf<SlotProfile>()
    private lateinit var rowAdapter: CharacterRowAdapter
    private var pendingReplaceIndex: Int = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_collection)

        recyclerView = findViewById(R.id.characterRecycler)
        recyclerView.layoutManager = GridLayoutManager(this@ChatCollectionActivity, 4)
        rebind()

        intent.getStringArrayListExtra("preSelectedIds")?.let { ids ->
            if (ids.isNotEmpty()) {
                fetchCharactersByIds(ids) { profiles ->
                    slotRoster.clear()
                    // Keep order of incoming ids; put a placeholder if something is missing
                    val byId = profiles.associateBy { it.id }
                    ids.forEachIndexed { i, id ->
                        val prof = byId[id]
                        if (prof != null) {
                            // Fill slot i with profile
                            val keepSlotId = "slot-${i + 1}-${System.currentTimeMillis()}"
                            slotRoster.add(prof.toSlot(i + 1, keepSlotId))
                        } else {
                            slotRoster.add(makePlaceholderSlot(i + 1))
                        }
                    }
                    rebind()
                }
            }
        }

        // Add Placeholder (just adds an empty slot; no picker)
        findViewById<Button>(R.id.btnAddPlaceholder).setOnClickListener {
            val index = slotRoster.size
            slotRoster.add(
                SlotProfile(
                    slotId = "slot-${index + 1}-${System.currentTimeMillis()}",
                    baseCharacterId = null,
                    name = "Empty Slot",
                    isPlaceholder = true
                )
            )
            rebind()
        }

        findViewById<Button>(R.id.btnAddCollection).setOnClickListener {
            loadUserCollections { userCollections ->
                if (userCollections.isEmpty()) {
                    Toast.makeText(this, "No collections found!", Toast.LENGTH_SHORT).show()
                    return@loadUserCollections
                }
                showCollectionPickerDialog(userCollections) { pickedCollection ->
                    addCollectionToSlots(pickedCollection)
                }
            }
        }

        // Add Individual (append a placeholder then immediately pick to fill it)
        findViewById<Button>(R.id.btnAddIndividual).setOnClickListener {
            val index = slotRoster.size
            slotRoster.add(
                com.example.RealmsAI.models.SlotProfile(
                    slotId = "slot-${index + 1}-${System.currentTimeMillis()}",
                    name = "Empty Slot",
                    isPlaceholder = true
                )
            )
            rebind()
            openPickerForIndex(index)
        }

        // Done → return slots (and optionally legacy profiles)
        findViewById<Button>(R.id.btnDone).setOnClickListener {
            val result = Intent().apply {
                putExtra("SLOT_ROSTER_JSON", Gson().toJson(slotRoster))
                putExtra("CHARACTER_LIST_JSON", Gson().toJson(slotRoster.map { it.toDisplay() }))
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    fun makePlaceholderSlot(slotIndex: Int): SlotProfile = SlotProfile(
        slotId = "slot-$slotIndex-${System.currentTimeMillis()}",
        baseCharacterId = null,
        name = "Empty Slot",
        isPlaceholder = true
    )

    private fun loadUserCollections(onLoaded: (List<CharacterCollection>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(userId).collection("collections")
            .get()
            .addOnSuccessListener { result ->
                val collections = result.documents.mapNotNull { it.toObject(CharacterCollection::class.java) }
                onLoaded(collections)
            }
            .addOnFailureListener { onLoaded(emptyList()) }
    }

    private fun showCollectionPickerDialog(
        collections: List<CharacterCollection>,
        onPick: (CharacterCollection) -> Unit
    ) {
        val displayNames = collections.map { it.name.ifBlank { "(Unnamed Collection)" } }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Collection")
            .setItems(displayNames.toTypedArray()) { _, which ->
                onPick(collections[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSlotActions(index: Int, slot: SlotProfile) {
        val options = arrayOf("Replace", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Slot ${index + 1}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openPickerForIndex(index)
                    1 -> {
                        slotRoster.removeAt(index)
                        rowAdapter.notifyItemRemoved(index)
                        rebind()
                    }                }
            }.show()
    }

    private fun openPickerForIndex(index: Int) {
        pendingReplaceIndex = index
        val intent = Intent(this, CharacterSelectionActivity::class.java)
        intent.putExtra("TEMP_SELECTION_MODE", true)
        intent.putExtra("MAX_SELECT", 1)
        intent.putExtra("from", "collections")
        startActivityForResult(intent, 2001)
    }

    fun CharacterProfile.toSlot(slotIndex: Int, keepSlotId: String? = null): SlotProfile =
        SlotProfile(
            slotId = keepSlotId ?: "slot-$slotIndex-${System.currentTimeMillis()}",
            baseCharacterId = this.id,
            name = this.name,
            summary = this.summary.orEmpty(),
            personality = this.personality,
            privateDescription = this.privateDescription,
            greeting = this.greeting,
            avatarUri = this.avatarUri,
            outfits = this.outfits,
            currentOutfit = this.currentOutfit,
            height = this.height,
            weight = this.weight,
            age = this.age,
            eyeColor = this.eyeColor,
            hairColor = this.hairColor,
            physicalDescription = this.physicalDescription,
            gender = this.gender,
            relationships = emptyList(),
            bubbleColor = this.bubbleColor,
            textColor = this.textColor,
            sfwOnly = this.sfwOnly,
            profileType = this.profileType,
            isPlaceholder = false
        )

    fun SlotProfile.toCharacterProfile(): CharacterProfile =
        CharacterProfile(
            id = this.baseCharacterId ?: "placeholder-${this.slotId}",
            name = if (isPlaceholder) "Empty Slot" else this.name,
            summary = if (isPlaceholder) "" else this.summary,
            personality = if (isPlaceholder) "" else this.personality,
            privateDescription = if (isPlaceholder) "" else this.privateDescription,
            greeting = if (isPlaceholder) "" else this.greeting,
            avatarUri = if (isPlaceholder) null else this.avatarUri,
            outfits = if (isPlaceholder) emptyList() else this.outfits,
            currentOutfit = if (isPlaceholder) "" else this.currentOutfit,
            height = this.height,
            weight = this.weight,
            age = this.age,
            eyeColor = this.eyeColor,
            hairColor = this.hairColor,
            physicalDescription = this.physicalDescription,
            gender = this.gender,
            relationships = if (isPlaceholder) emptyList() else this.relationships,
            bubbleColor = this.bubbleColor,
            textColor = this.textColor,
            sfwOnly = this.sfwOnly,
            profileType = if (isPlaceholder) "placeholder" else this.profileType
        )

    private fun addCollectionToSlots(collection: CharacterCollection) {
        // 1) De-dupe against what's already in the roster
        val existingBaseIds = slotRoster.mapNotNull { it.baseCharacterId }.toSet()
        val uniqueIds = collection.characterIds.distinct().filter { it !in existingBaseIds }
        if (uniqueIds.isEmpty()) {
            Toast.makeText(this, "All characters already added.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2) Fetch profiles in chunks of 10
        fetchCharactersByIds(uniqueIds) { fetched ->
            if (fetched.isEmpty()) {
                Toast.makeText(this, "No characters found.", Toast.LENGTH_SHORT).show()
                return@fetchCharactersByIds
            }

            // 3) Insert: fill placeholders first, then append new slots
            fetched.forEach { prof ->
                // Find first placeholder
                val phIndex = slotRoster.indexOfFirst { it.isPlaceholder }
                if (phIndex >= 0) {
                    val keepId = slotRoster[phIndex].slotId
                    slotRoster[phIndex] = prof.toSlot(phIndex + 1, keepSlotId = keepId)
                } else {
                    val newIndex = slotRoster.size + 1
                    slotRoster.add(prof.toSlot(newIndex))
                }
            }

            // 4) Refresh UI once
            rebind()
            Toast.makeText(this, "Added ${fetched.size} from collection.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Firestore helper: get CharacterProfile docs by document IDs with chunking */
    private fun fetchCharactersByIds(ids: List<String>, onLoaded: (List<CharacterProfile>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val chunks = ids.chunked(10)
        val results = mutableListOf<CharacterProfile>()
        var remaining = chunks.size
        if (remaining == 0) { onLoaded(emptyList()); return }

        chunks.forEach { group ->
            db.collection("characters")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), group)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc ->
                        doc.toObject(CharacterProfile::class.java)?.let { cp ->
                            // prefer doc.id as canonical id
                            results += cp.copy(id = doc.id)
                        }
                    }
                }
                .addOnCompleteListener {
                    remaining--
                    if (remaining == 0) {
                        // Keep same order as the original ids where possible
                        val byId = results.associateBy { it.id }
                        val ordered = ids.mapNotNull { byId[it] }
                        onLoaded(ordered)
                    }
                }
        }
    }

    private fun updateCharacterPreview() {
        val previews = selectedCharacters.map {
            CharacterPreview(
                id = it.id,
                name = it.name,
                summary = it.summary.orEmpty(),
                avatarUri = it.avatarUri,
                avatarResId = it.avatarResId ?: R.drawable.placeholder_avatar,
                author = it.author,
                rawJson = Gson().toJson(it)
            )
        }
        adapter.updateList(previews)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        if (requestCode == 2001) {
            val index = pendingReplaceIndex
            pendingReplaceIndex = -1
            val ids = data.getStringArrayListExtra("selectedCharacterIds") ?: return
            if (index !in slotRoster.indices || ids.isEmpty()) return
            val id = ids.first()
            FirebaseFirestore.getInstance().collection("characters")
                .document(id).get()
                .addOnSuccessListener { doc ->
                    val prof = doc.toObject(CharacterProfile::class.java) ?: return@addOnSuccessListener
                    val keep = slotRoster[index].slotId
                    slotRoster[index] = prof.copy(id = doc.id).toSlot(index + 1, keepSlotId = keep)
                    rebind()
                }
        }
    }

    private fun SlotProfile.toDisplay(): CharacterProfile =
        CharacterProfile(
            id = baseCharacterId ?: "placeholder-$slotId",
            name = if (isPlaceholder) "Empty Slot" else name,
            summary = if (isPlaceholder) "" else summary,
            personality = if (isPlaceholder) "" else personality,
            privateDescription = if (isPlaceholder) "" else privateDescription,
            greeting = if (isPlaceholder) "" else greeting,
            avatarUri = if (isPlaceholder) null else avatarUri,
            outfits = if (isPlaceholder) emptyList() else outfits,
            currentOutfit = if (isPlaceholder) "" else currentOutfit,
            height = height,
            weight = weight,
            age = age,
            eyeColor = eyeColor,
            hairColor = hairColor,
            physicalDescription = physicalDescription,
            gender = gender,
            relationships = if (isPlaceholder) emptyList() else relationships,
            bubbleColor = bubbleColor,
            textColor = textColor,
            sfwOnly = sfwOnly,
            profileType = if (isPlaceholder) "placeholder" else profileType
        )

    private fun buildDisplayList(): List<CharacterProfile> = slotRoster.map { it.toDisplay() }
    private fun slotKey(s: SlotProfile) = s.baseCharacterId ?: "placeholder-${s.slotId}"
    private fun slotIndexOf(cp: CharacterProfile) = slotRoster.indexOfFirst { slotKey(it) == cp.id }

    private fun rebind() {
        rowAdapter = CharacterRowAdapter(
            characters = buildDisplayList(),
            onClick = { cp ->
                val idx = slotIndexOf(cp)
                if (idx != -1) openPickerForIndex(idx)   // replace-in-place (works for placeholder or real)
            },
            onBindVisuals = { cp, itemView ->
                val idx = slotIndexOf(cp)
                itemView.setOnLongClickListener {
                    if (idx != -1) showSlotActions(idx, slotRoster[idx])
                    true
                }
                // ghost look for placeholders
                val isPh = cp.profileType == "placeholder" || cp.id.startsWith("placeholder-")
                val img = itemView.findViewById<ImageView>(R.id.character_image)
                img?.imageAlpha = if (isPh) 128 else 255
            }
        )
        recyclerView.adapter = rowAdapter
    }

}
