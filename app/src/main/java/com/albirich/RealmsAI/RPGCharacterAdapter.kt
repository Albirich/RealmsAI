package com.albirich.RealmsAI

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.CharacterLink
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.ModeSettings.CharacterClass
import com.albirich.RealmsAI.models.ModeSettings.CharacterRole
import com.albirich.RealmsAI.models.ModeSettings.RPGCharacter
import com.albirich.RealmsAI.models.ModeSettings.RPGGenre

class RPGCharacterAdapter(
    private val characterProfiles: List<CharacterProfile>,  // the imported list
    private val rpgCharacters: MutableList<RPGCharacter>,   // must match order!
    private var genre: RPGGenre,
    private val linkedToMap: Map<String, List<CharacterLink>>,
    private val onLinkedToMapUpdate: (String, CharacterLink?) -> Unit,
    private val onTheTable: Boolean
) : RecyclerView.Adapter<RPGCharacterAdapter.ViewHolder>() {

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.rpgCharacterAvatar)
        val name: TextView = view.findViewById(R.id.characterName)
        val roleSpinner: Spinner = view.findViewById(R.id.roleSpinner)
        val classSpinner: Spinner = view.findViewById(R.id.classSpinner)
        val sidekickRow: LinearLayout = view.findViewById(R.id.sidekickRow)
        val sidekickSpinner: Spinner = view.findViewById(R.id.sidekickSpinner)
        val traitorCheck: CheckBox = view.findViewById(R.id.traitorCheck)
        // Stat inputs
        val strengthEdit: EditText = view.findViewById(R.id.strengthEdit)
        val agilityEdit: EditText = view.findViewById(R.id.agilityEdit)
        val intelligenceEdit: EditText = view.findViewById(R.id.intelligenceEdit)
        val charismaEdit: EditText = view.findViewById(R.id.charismaEdit)
        val resolveEdit: EditText = view.findViewById(R.id.resolveEdit)

        // Stat modifiers
        val strengthMod: TextView = view.findViewById(R.id.strengthMod)
        val agilityMod: TextView = view.findViewById(R.id.agilityMod)
        val intelligenceMod: TextView = view.findViewById(R.id.intelligenceMod)
        val charismaMod: TextView = view.findViewById(R.id.charismaMod)
        val resolveMod: TextView = view.findViewById(R.id.resolveMod)

        // Points left
        val pointsLeftText: TextView = view.findViewById(R.id.pointsLeftText)

        // Equipment
        val equipmentEdit: EditText = view.findViewById(R.id.equipmentEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rpg_character, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = characterProfiles.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val charProfile = characterProfiles[position]
        val rpgChar = rpgCharacters[position]


        // Set avatar & name (use image loading as needed)
        if (!charProfile.avatarUri.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(charProfile.avatarUri)
                .circleCrop() // Optional: Makes the avatars perfectly round!
                .into(holder.avatar) // Replace with your actual ImageView ID
        } else {
            // Fallback to placeholder if they don't have an avatar
            holder.avatar.setImageResource(R.drawable.placeholder_avatar)
        }
        holder.name.text = charProfile.name

        // Set up spinners (roles, classes)
        val availableRoles = if (onTheTable) {
            CharacterRole.values().filter { it != CharacterRole.GM }
        } else {
            CharacterRole.values().toList()
        }

        val roleNames = availableRoles.map { it.name.replace('_', ' ').capitalize() }

        val roleAdapter = ArrayAdapter(
            holder.view.context,
            android.R.layout.simple_spinner_dropdown_item,
            roleNames
        )

        holder.roleSpinner.adapter = roleAdapter

        // Adjust selection based on filtered list
        val selectedRoleIndex = availableRoles.indexOf(rpgChar.role).coerceAtLeast(0)
        holder.roleSpinner.setSelection(selectedRoleIndex)

        holder.roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                rpgChar.role = availableRoles[pos]
                holder.sidekickRow.visibility = if (rpgChar.role == CharacterRole.SIDEKICK) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val classList = CharacterClass.values().filter { it.genres.contains(genre) }

        val adapter = ArrayAdapter(
            holder.view.context,
            android.R.layout.simple_spinner_item,
            classList.map { it.name.replace('_', ' ').lowercase().replaceFirstChar { char -> char.uppercase() } }
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        holder.classSpinner.adapter = adapter

        val currentIndex = classList.indexOfFirst { it == rpgChar.characterClass }.coerceAtLeast(0)
        holder.classSpinner.setSelection(currentIndex)

        holder.classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                rpgChar.characterClass = classList[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // List all characters except self for sidekick targets
        val sidekickNames = characterProfiles.filter { it.id != charProfile.id }.map { it.name }
        val sidekickIds = characterProfiles.filter { it.id != charProfile.id }.map { it.id }

        Log.d("DEBUG", "sidekickNames: $sidekickNames")

            val spinnerAdapter = ArrayAdapter(holder.view.context, android.R.layout.simple_spinner_dropdown_item, sidekickNames)
            holder.sidekickSpinner.adapter = spinnerAdapter

            val linkedList = linkedToMap[charProfile.id] ?: emptyList()
            val sidekickLink = linkedList.firstOrNull { it.type == "sidekickTo" }
            val currentTargetId = sidekickLink?.targetId
            val selectedIndex = sidekickIds.indexOf(currentTargetId)

            var spinnerReady = false
            if (selectedIndex >= 0) holder.sidekickSpinner.setSelection(selectedIndex)
            spinnerReady = true

            holder.sidekickSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    if (!spinnerReady) return
                    val chosenId = sidekickIds[pos]
                    val traitorNotes = if (holder.traitorCheck.isChecked) "traitor" else ""
                    onLinkedToMapUpdate(
                        charProfile.id,
                        CharacterLink(
                            targetId = chosenId,
                            type = "sidekickTo",
                            trigger = "",
                            notes = traitorNotes
                        )
                    )
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }



        // Traitor toggle logic
        holder.traitorCheck.setOnCheckedChangeListener { _, isChecked ->
            val currentLink = linkedToMap[charProfile.id]?.firstOrNull { it.type == "sidekickTo" }
            if (currentLink != null) {
                // Update the notes field
                onLinkedToMapUpdate(
                    charProfile.id,
                    currentLink.copy(notes = if (isChecked) "traitor" else "")
                )
            }
        }


        // -- STAT LOGIC --
        val statEdits = listOf(holder.strengthEdit, holder.agilityEdit, holder.intelligenceEdit, holder.charismaEdit, holder.resolveEdit)
        val statGetters = arrayOf(
            { rpgChar.stats.strength },
            { rpgChar.stats.agility },
            { rpgChar.stats.intelligence },
            { rpgChar.stats.charisma },
            { rpgChar.stats.resolve }
        )
        val statSetters = arrayOf<(Int) -> Unit>(
            { v -> rpgChar.stats.strength = v },
            { v -> rpgChar.stats.agility = v },
            { v -> rpgChar.stats.intelligence = v },
            { v -> rpgChar.stats.charisma = v },
            { v -> rpgChar.stats.resolve = v }
        )


        // Fill stat values
        holder.strengthEdit.setText(rpgChar.stats.strength.toString())
        holder.agilityEdit.setText(rpgChar.stats.agility.toString())
        holder.intelligenceEdit.setText(rpgChar.stats.intelligence.toString())
        holder.charismaEdit.setText(rpgChar.stats.charisma.toString())
        holder.resolveEdit.setText(rpgChar.stats.resolve.toString())


        // Watch for changes and enforce stat pool/min/max
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Update stats from UI
                var total = 0
                for (i in statEdits.indices) {
                    val value = statEdits[i].text.toString().toIntOrNull() ?: 1
                    val newValue = value.coerceIn(1, 10)
                    statSetters[i](newValue)
                    if (value != newValue) {
                        statEdits[i].setText(newValue.toString())
                        statEdits[i].setSelection(statEdits[i].text.length)
                    }
                    total += newValue
                }
                // Enforce pool
                val pointsLeft = 30 - total
                holder.pointsLeftText.text = pointsLeft.toString()
                if (pointsLeft < 0) {
                    holder.pointsLeftText.setTextColor(0xFFFF4444.toInt()) // red
                } else if (pointsLeft == 0) {
                    holder.pointsLeftText.setTextColor(0xFF3F51B5.toInt()) // blue
                } else {
                    holder.pointsLeftText.setTextColor(0xFF555555.toInt()) // gray
                }
                // Show modifiers
                val mods = listOf(
                    statModifier(rpgChar.stats.strength),
                    statModifier(rpgChar.stats.agility),
                    statModifier(rpgChar.stats.intelligence),
                    statModifier(rpgChar.stats.charisma),
                    statModifier(rpgChar.stats.resolve)
                )
                holder.strengthMod.text = modString(mods[0])
                holder.agilityMod.text = modString(mods[1])
                holder.intelligenceMod.text = modString(mods[2])
                holder.charismaMod.text = modString(mods[3])
                holder.resolveMod.text = modString(mods[4])
            }
        }
        // Attach watcher to all stat fields
        for (edit in statEdits) {
            edit.addTextChangedListener(watcher)
        }
        // Trigger once on bind
        watcher.afterTextChanged(null)

        // Equipment logic
        holder.equipmentEdit.setText(rpgChar.equipment.joinToString(", "))
        holder.equipmentEdit.filters = arrayOf(android.text.InputFilter.LengthFilter(200))
        holder.equipmentEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val list = s.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (list.size > 10) {
                    holder.equipmentEdit.error = "Max 10 items allowed"
                } else {
                    holder.equipmentEdit.error = null
                }

                rpgChar.equipment = list
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    fun getCharacters(): List<RPGCharacter> = rpgCharacters
    fun setGenre(newGenre: RPGGenre) { genre = newGenre; notifyDataSetChanged() }

    companion object {
        fun statModifier(value: Int): Int = value / 2
        fun modString(mod: Int) = if (mod >= 0) "+$mod" else "$mod"
    }
}
