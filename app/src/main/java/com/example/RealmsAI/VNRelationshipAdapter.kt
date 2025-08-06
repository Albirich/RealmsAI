package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ModeSettings.VNRelationship
import com.example.RealmsAI.models.Relationship
import com.example.RealmsAI.models.SlotProfile
import com.google.gson.Gson

class VNRelationshipAvatarAdapter(
    private val otherCharacters: List<CharacterProfile>,
    private val mainCharId: String,
    private val relationshipBoard: MutableMap<String, VNRelationship>,
    private val onEdit: (VNRelationship, CharacterProfile) -> Unit
) : RecyclerView.Adapter<VNRelationshipAvatarAdapter.AvatarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vn_relationship_avatar, parent, false)
        return AvatarViewHolder(view)
    }

    override fun getItemCount() = otherCharacters.size

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val char = otherCharacters[position]
        holder.nameView.text = char.name
        // TODO: Load avatar into holder.avatarView

        val rel = relationshipBoard.getOrPut(char.id) { VNRelationship(fromId = mainCharId, toId = char.id) }
        holder.avatarView.setOnClickListener {
            onEdit(rel, char)
        }
    }

    inner class AvatarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarView: ImageView = view.findViewById(R.id.characterAvatar)
        val nameView: TextView = view.findViewById(R.id.characterName)
    }
}

class VNRelationshipBoardAdapter(
    private val characters: List<CharacterProfile>,
    private val characterBoards: MutableMap<String, MutableMap<String, VNRelationship>>,
    private val onEdit: (VNRelationship, CharacterProfile, CharacterProfile) -> Unit
) : RecyclerView.Adapter<VNRelationshipBoardAdapter.BoardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_relationship_board, parent, false)
        return BoardViewHolder(view)
    }

    override fun getItemCount() = characters.size

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val mc = characters[position]
        holder.mainCharName.text = mc.name
        // All others except self
        val otherChars = characters.filter { it.id != mc.id }
        // Get or create the board for this MC
        val relBoard = characterBoards.getOrPut(mc.id) { mutableMapOf() }

        // Set up inner horizontal RecyclerView
        holder.innerRecycler.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.innerRecycler.adapter = VNRelationshipAvatarAdapter(
            otherChars, mc.id, relBoard
        ) { rel, toChar ->
            onEdit(rel, mc, toChar)
        }
    }

    inner class BoardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mainCharName: TextView = view.findViewById(R.id.mainCharName)
        val innerRecycler: RecyclerView = view.findViewById(R.id.innerRelationshipRecycler)
    }
}

