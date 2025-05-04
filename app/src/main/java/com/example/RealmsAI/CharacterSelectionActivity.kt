package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

class CharacterSelectionActivity : AppCompatActivity() {
    private val selectedIds = mutableSetOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_character)

        // 1) Load all chars from prefs
        val prefs = getSharedPreferences("characters", MODE_PRIVATE)
        val allChars = prefs.all.mapNotNull { (key, json) ->
            JSONObject(json as String).let { obj ->
                Character(
                    id = key,
                    name = obj.optString("name"),
                    avatarUri = obj.optString("avatarUri", null)
                )
            }
        }

        // 2) Pre-select if passed in
        intent.getStringArrayListExtra("PRESELECTED_CHARS")?.let {
            selectedIds.addAll(it)
        }

        // 3) Setup RecyclerView + adapter
        val recycler = findViewById<RecyclerView>(R.id.characterRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = CharacterSelectAdapter(allChars, selectedIds) { charId, isNowSelected ->
            if (isNowSelected) selectedIds += charId else selectedIds -= charId
        }
        recycler.adapter = adapter

        // 4) Done → return list
        findViewById<MaterialButton>(R.id.doneButton).setOnClickListener {
            setResult(
                RESULT_OK,
                Intent().apply {
                    putStringArrayListExtra("SELECTED_CHARS", ArrayList(selectedIds))
                }
            )
            finish()
        }
    }
}
