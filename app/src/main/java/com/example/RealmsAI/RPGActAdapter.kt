package com.example.RealmsAI

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.Area
import com.example.RealmsAI.models.ModeSettings.RPAct

class RPGActAdapter(
    private val acts: MutableList<RPAct>,
    private val areas: List<Area>
) : RecyclerView.Adapter<RPGActAdapter.ViewHolder>() {

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.actHeader)
        val expandButton: ImageButton = view.findViewById(R.id.expandButton)
        val summaryEdit: EditText = view.findViewById(R.id.actSummary)
        val goalEdit: EditText = view.findViewById(R.id.actGoal)
        val areaSpinner: Spinner = view.findViewById(R.id.areaSpinner)
        var isExpanded = true // Expand all by default, set to false if you want collapsed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_act, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = acts.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val act = acts[position]

        holder.header.text = "ACT ${position + 1}"

        // Expand/collapse logic (optional)
        fun setExpanded(expanded: Boolean) {
            holder.summaryEdit.visibility = if (expanded) View.VISIBLE else View.GONE
            holder.goalEdit.visibility = if (expanded) View.VISIBLE else View.GONE
            holder.areaSpinner.visibility = if (expanded) View.VISIBLE else View.GONE
            holder.expandButton.setImageResource(
                if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            holder.isExpanded = expanded
        }
        setExpanded(holder.isExpanded)
        holder.expandButton.setOnClickListener {
            setExpanded(!holder.isExpanded)
        }

        // EditTexts - keep in sync
        holder.summaryEdit.setText(act.summary)
        holder.goalEdit.setText(act.goal)

        holder.summaryEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { act.summary = s?.toString() ?: "" }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        holder.goalEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { act.goal = s?.toString() ?: "" }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Area Spinner
        val areaNames = areas.map { it.name }
        holder.areaSpinner.adapter = ArrayAdapter(
            holder.view.context,
            android.R.layout.simple_spinner_dropdown_item,
            areaNames
        )
        val selectedIndex = areas.indexOfFirst { it.id == act.areaId }.coerceAtLeast(0)
        holder.areaSpinner.setSelection(selectedIndex)
        holder.areaSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                act.areaId = areas[pos].id
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    fun addAct(newAct: RPAct) {
        acts.add(newAct)
        Log.d("RPGActAdapter", "Added new act. Total acts: ${acts.size}")
        notifyItemInserted(acts.size - 1)
    }

    fun removeAct(position: Int) {
        if (acts.isNotEmpty() && position in acts.indices) {
            acts.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getActs(): List<RPAct> = acts
}
