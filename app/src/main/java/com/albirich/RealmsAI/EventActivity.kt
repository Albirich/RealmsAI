package com.albirich.RealmsAI

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.ScenarioEvent
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson

class EventsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENTS_JSON = "EXTRA_EVENTS_JSON"
    }

    private val eventList = mutableListOf<ScenarioEvent>()
    private lateinit var eventAdapter: EventAdapter
    private lateinit var eventsRecycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        eventsRecycler = findViewById(R.id.eventsRecycler)
        val addEventBtn = findViewById<MaterialButton>(R.id.addEventButton)
        val saveBtn = findViewById<MaterialButton>(R.id.saveEventsButton)

        // 1. Load Existing Events
        val eventsJson = intent.getStringExtra(EXTRA_EVENTS_JSON)
        if (!eventsJson.isNullOrBlank()) {
            val loaded = Gson().fromJson(eventsJson, Array<ScenarioEvent>::class.java).toList()
            eventList.addAll(loaded)
        }

        // 2. Setup Adapter
        eventAdapter = EventAdapter(
            events = eventList,
            onDeleteEvent = { position ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Event?")
                    .setMessage("Are you sure you want to remove this scenario event?")
                    .setPositiveButton("Delete") { _, _ ->
                        eventList.removeAt(position)
                        eventAdapter.notifyItemRemoved(position)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        eventsRecycler.layoutManager = LinearLayoutManager(this)
        eventsRecycler.adapter = eventAdapter

        // 3. Add Event Button
        addEventBtn.setOnClickListener {
            eventList.add(ScenarioEvent(title = "New Event"))
            eventAdapter.notifyItemInserted(eventList.size - 1)
            eventsRecycler.scrollToPosition(eventList.size - 1)
        }

        // 4. Save Button
        saveBtn.setOnClickListener {
            // Clean up empty events before saving
            val cleanedEvents = eventList.filter {
                it.title.isNotBlank() || it.description.isNotBlank() || it.narratorMessage.isNotBlank()
            }

            val json = Gson().toJson(cleanedEvents)
            val resultIntent = Intent().apply {
                putExtra(EXTRA_EVENTS_JSON, json)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}