package com.example.RealmsAI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ModeSettings
import com.example.RealmsAI.models.ModeSettings.VNRelationship

class VNRelationshipAvatarAdapter(
    private val otherCharacters: List<CharacterProfile>,
    private val selectedCharacters: List<CharacterProfile>,
    private val mainSlotKey: String,
    private val relationshipBoardBySlot: MutableMap<String, VNRelationship>,
    private val onEdit: (VNRelationship, CharacterProfile) -> Unit
) : RecyclerView.Adapter<VNRelationshipAvatarAdapter.AvatarViewHolder>()  {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vn_relationship_avatar, parent, false)
        return AvatarViewHolder(view)
    }

    override fun getItemCount() = otherCharacters.size

    private fun slotKeyFor(p: CharacterProfile): String {
        val idx = selectedCharacters.indexOfFirst { it.id == p.id }
        return ModeSettings.SlotKeys.fromPosition(idx)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val char = otherCharacters[position]
        holder.nameView.text = char.name

        val otherKey = slotKeyFor(char)

        // Store by SLOT KEY and make VNRelationship use slot keys
        val rel = relationshipBoardBySlot.getOrPut(otherKey) {
            VNRelationship(fromSlotKey = otherKey, toSlotKey = mainSlotKey)
        }

        holder.avatarView.setOnClickListener { onEdit(rel, char) }
    }

    inner class AvatarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarView: ImageView = view.findViewById(R.id.characterAvatar)
        val nameView: TextView = view.findViewById(R.id.characterName)
    }
}


class VNRelationshipBoardAdapter(
    private val characters: List<CharacterProfile>,
    private val selectedCharacters: List<CharacterProfile>, // for slot keys
    private val characterBoardsBySlot: MutableMap<String, MutableMap<String, VNRelationship>>,
    private val onEdit: (VNRelationship, CharacterProfile, CharacterProfile) -> Unit
) : RecyclerView.Adapter<VNRelationshipBoardAdapter.BoardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_relationship_board, parent, false)
        return BoardViewHolder(view)
    }

    override fun getItemCount() = characters.size

    private fun slotKeyFor(p: CharacterProfile): String {
        val idx = selectedCharacters.indexOfFirst { it.id == p.id }
        return ModeSettings.SlotKeys.fromPosition(idx)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val mc = characters[position]
        holder.mainCharName.text = mc.name

        val otherChars = characters.filter { it.id != mc.id }

        val mcKey = slotKeyFor(mc)
        val perFromMap: MutableMap<String, VNRelationship> =
            characterBoardsBySlot.getOrPut(mcKey) { mutableMapOf() } // from = mcKey

        holder.innerRecycler.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.innerRecycler.adapter = VNRelAvatarAdapter_FromFixed(
            otherCharacters = otherChars,
            selectedCharacters = selectedCharacters,
            fromSlotKey = mcKey,           // fixed "from"
            perFromMap = perFromMap,       // keyed by toSlotKey
            onEdit = { rel, toChar -> onEdit(rel, mc, toChar) }
        )

        // (Optional) smooth: disable nested scroll if inside ScrollView
        holder.innerRecycler.isNestedScrollingEnabled = false
    }

    inner class BoardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mainCharName: TextView = view.findViewById(R.id.mainCharName)
        val innerRecycler: RecyclerView = view.findViewById(R.id.innerRelationshipRecycler)
    }
}


class VNRelAvatarAdapter_OthersToMain(
    private val otherCharacters: List<CharacterProfile>,
    private val selectedCharacters: List<CharacterProfile>,
    private val mainSlotKey: String,
    private val characterBoards: MutableMap<String, MutableMap<String, VNRelationship>>,
    private val onEdit: (VNRelationship, CharacterProfile) -> Unit
) : RecyclerView.Adapter<VNRelAvatarAdapter_OthersToMain.AvatarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vn_relationship_avatar, parent, false)
        return AvatarViewHolder(view)
    }

    override fun getItemCount() = otherCharacters.size

    private fun slotKeyFor(p: CharacterProfile): String {
        val idx = selectedCharacters.indexOfFirst { it.id == p.id }
        return ModeSettings.SlotKeys.fromPosition(idx)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val otherChar = otherCharacters[position]
        holder.nameView.text = otherChar.name
        val avatarUrl = otherChar.avatarUri // <- make sure CharacterProfile has this field
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(holder.avatarView.context)
                .load(avatarUrl)
                .placeholder(R.drawable.placeholder_avatar) // optional
                .error(R.drawable.placeholder_avatar)             // optional
                .into(holder.avatarView)
        } else {
            holder.avatarView.setImageResource(R.drawable.placeholder_avatar)
        }
        val otherKey = slotKeyFor(otherChar)

        // from = otherKey, to = mainSlotKey
        val fromMap = characterBoards.getOrPut(otherKey) { mutableMapOf() }
        val rel = fromMap.getOrPut(mainSlotKey) {
            VNRelationship(fromSlotKey = otherKey, toSlotKey = mainSlotKey)
        }

        holder.avatarView.setOnClickListener { onEdit(rel, otherChar) }
    }

    inner class AvatarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarView: ImageView = view.findViewById(R.id.characterAvatar)
        val nameView: TextView = view.findViewById(R.id.characterName)
    }
}

class VNRelAvatarAdapter_FromFixed(
    private val otherCharacters: List<CharacterProfile>,
    private val selectedCharacters: List<CharacterProfile>,
    private val fromSlotKey: String,                               // fixed "from"
    private val perFromMap: MutableMap<String, VNRelationship>,    // key: toSlotKey
    private val onEdit: (VNRelationship, CharacterProfile) -> Unit
) : RecyclerView.Adapter<VNRelAvatarAdapter_FromFixed.AvatarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vn_relationship_avatar, parent, false)
        return AvatarViewHolder(view)
    }

    override fun getItemCount() = otherCharacters.size

    private fun slotKeyFor(p: CharacterProfile): String {
        val idx = selectedCharacters.indexOfFirst { it.id == p.id }
        return ModeSettings.SlotKeys.fromPosition(idx)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val toChar = otherCharacters[position]
        holder.nameView.text = toChar.name
        val avatarUrl = toChar.avatarUri  // e.g. String? in CharacterProfile
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(holder.avatarView.context)
                .load(avatarUrl)
                .placeholder(R.drawable.placeholder_avatar) // optional
                .error(R.drawable.placeholder_avatar)             // optional
                .into(holder.avatarView)
        } else {
            holder.avatarView.setImageResource(R.drawable.placeholder_avatar)
        }

        val toKey = slotKeyFor(toChar)
        val rel = perFromMap.getOrPut(toKey) {
            VNRelationship(fromSlotKey = fromSlotKey, toSlotKey = toKey)
        }
        holder.avatarView.setOnClickListener { onEdit(rel, toChar) }
    }

    inner class AvatarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarView: ImageView = view.findViewById(R.id.characterAvatar)
        val nameView: TextView = view.findViewById(R.id.characterName)
    }
}


