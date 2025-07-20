package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.Outfit
import com.example.RealmsAI.models.PersonaProfile
import com.example.RealmsAI.models.PoseSlot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson

class PersonaCreationActivity : AppCompatActivity() {
    // UI
    private lateinit var nameEt: EditText
    private lateinit var ageEt: EditText
    private lateinit var genderEt: EditText
    private lateinit var heightEt: EditText
    private lateinit var weightEt: EditText
    private lateinit var hairEt: EditText
    private lateinit var eyesEt: EditText
    private lateinit var descEt: EditText
    private lateinit var avatarView: ImageView
    private lateinit var wardrobeBtn: Button
    private lateinit var bubbleColorSpinner: Spinner
    private lateinit var textColorSpinner: Spinner
    private lateinit var saveBtn: Button

    // State
    private var avatarUri: Uri? = null
    private var avatarStringUri: String = ""
    private var outfitsList: List<Outfit> = emptyList()
    private var currentOutfit: String = ""
    private var editPersonaId: String? = null

    private val bubblecolorOptions = listOf(
        "Blue" to "#2196F3",
        "Green" to "#4CAF50",
        "Orange" to "#FF9800",
        "Pink" to "#e86cbe",
        "Purple" to "#c778f5",
        "White" to "#FFFFFF",
        "Yellow" to "#FFEB3B"
    )
    private val textcolorOptions = listOf(
        "Black" to "#000000",
        "Blue" to "#213af3",
        "Green" to "#098217",
        "Orange" to "#cd6a00",
        "Pink" to "#E91E63",
        "Purple" to "#A200FF",
        "Red" to "#ce0202",
        "Yellow" to "#cdd54b"
    )

    companion object {
        const val WARDROBE_REQUEST = 101
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_persona)

        // Bind views
        nameEt    = findViewById(R.id.personaNameInput)
        ageEt     = findViewById(R.id.personaAgeInput)
        genderEt  = findViewById(R.id.personaGenderInput)
        heightEt  = findViewById(R.id.personaHeightInput)
        weightEt  = findViewById(R.id.personaWeightInput)
        hairEt    = findViewById(R.id.personaHairInput)
        eyesEt    = findViewById(R.id.personaEyesInput)
        descEt    = findViewById(R.id.personaDescriptionInput)
        avatarView= findViewById(R.id.personaAvatarView)
        wardrobeBtn= findViewById(R.id.wardrobeButton)
        bubbleColorSpinner = findViewById(R.id.bubbleColorSpinner)
        textColorSpinner = findViewById(R.id.textColorSpinner)
        saveBtn   = findViewById(R.id.savePersonaButton)

        // Color spinners
        val bubbleadapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bubblecolorOptions.map { it.first })
        bubbleadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bubbleColorSpinner.adapter = bubbleadapter
        val textadapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, textcolorOptions.map { it.first })
        textadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        textColorSpinner.adapter = textadapter

        // Avatar picker
        val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                avatarUri = it
                avatarStringUri = it.toString()
                Glide.with(this)
                    .load(it)
                    .placeholder(R.drawable.placeholder_avatar)
                    .into(avatarView)
            }
        }
        avatarView.setOnClickListener { avatarPicker.launch("image/*") }

        // Wardrobe picker
        wardrobeBtn.setOnClickListener {
            val intent = Intent(this, WardrobeActivity::class.java)
            val outfitsJson = Gson().toJson(outfitsList)
            intent.putExtra(EXTRA_OUTFITS_JSON, outfitsJson)
            startActivityForResult(intent, WARDROBE_REQUEST)
        }

        // If editing
        editPersonaId = intent.getStringExtra("PERSONA_EDIT_ID")
        val personaJson = intent.getStringExtra("PERSONA_PROFILE_JSON")
        if (editPersonaId != null && !personaJson.isNullOrBlank()) {
            val profile = Gson().fromJson(personaJson, PersonaProfile::class.java)
            fillFormFromProfile(profile)
        } else {
            avatarView.setImageResource(R.drawable.placeholder_avatar)
        }

        saveBtn.setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                Toast.makeText(this, "You must be logged in to save a persona.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val personaId = editPersonaId ?: System.currentTimeMillis().toString()
            val bubblecolorHexList = bubblecolorOptions.map { it.second }
            val textcolorHexList = textcolorOptions.map { it.second }
            val bubbleColorHex = bubblecolorHexList[bubbleColorSpinner.selectedItemPosition]
            val textColorHex = textcolorHexList[textColorSpinner.selectedItemPosition]

            val ageStr = ageEt.text.toString().trim()
            val age = ageStr.toIntOrNull() ?: 0

            val persona = PersonaProfile(
                id          = personaId,
                name        = nameEt.text.toString().trim(),
                age         = age,
                gender      = genderEt.text.toString().trim(),
                height      = heightEt.text.toString().trim(),
                weight      = weightEt.text.toString().trim(),
                hair        = hairEt.text.toString().trim(),
                eyes        = eyesEt.text.toString().trim(),
                physicaldescription = descEt.text.toString().trim(),
                outfits     = outfitsList,
                currentOutfit = currentOutfit,
                author      = userId,
                bubbleColor = bubbleColorHex,
                textColor = textColorHex,
                avatarUri   = avatarStringUri,
            )
            uploadAllPosesAndAvatarAndSavePersona(persona)
        }
    }

    private fun fillFormFromProfile(profile: PersonaProfile) {
        nameEt.setText(profile.name)
        ageEt.setText(profile.age.toString())
        genderEt.setText(profile.gender)
        heightEt.setText(profile.height)
        weightEt.setText(profile.weight)
        hairEt.setText(profile.hair)
        eyesEt.setText(profile.eyes)
        descEt.setText(profile.physicaldescription)
        outfitsList = profile.outfits
        currentOutfit = profile.currentOutfit

        // Pick a "neutral" pose image if possible, else fallback to avatarUri
        val avatarUrl = profile.outfits
            .firstOrNull { it.name == profile.currentOutfit }
            ?.poseSlots?.firstOrNull { it.name.equals("neutral", true) }?.uri
            ?: profile.outfits.firstOrNull()?.poseSlots?.firstOrNull()?.uri
            ?: profile.avatarUri

        avatarStringUri = avatarUrl ?: ""
        avatarUri = if (!avatarStringUri.isBlank() && avatarStringUri.startsWith("content://")) Uri.parse(avatarStringUri) else null

        if (!avatarStringUri.isNullOrBlank()) {
            Glide.with(this)
                .load(avatarStringUri)
                .placeholder(R.drawable.placeholder_avatar)
                .into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.placeholder_avatar)
        }

        val bubblecolorHexList = bubblecolorOptions.map { it.second }
        val textcolorHexList = textcolorOptions.map { it.second }
        bubbleColorSpinner.setSelection(bubblecolorHexList.indexOf(profile.bubbleColor).takeIf { it >= 0 } ?: 0)
        textColorSpinner.setSelection(textcolorHexList.indexOf(profile.textColor).takeIf { it >= 0 } ?: 0)
    }

    // Handles wardrobe result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WARDROBE_REQUEST && resultCode == Activity.RESULT_OK) {
            val outfitsJson = data?.getStringExtra(EXTRA_OUTFITS_JSON)
            if (!outfitsJson.isNullOrEmpty()) {
                val outfits = Gson().fromJson(outfitsJson, Array<Outfit>::class.java)
                outfitsList = outfits.toList()
                currentOutfit = outfitsList.firstOrNull()?.name ?: ""
                // Optionally update avatarView to "neutral" pose
                val neutralPose = outfitsList.firstOrNull()?.poseSlots?.firstOrNull { it.name.equals("neutral", true) }?.uri
                if (!neutralPose.isNullOrBlank()) {
                    Glide.with(this)
                        .load(neutralPose)
                        .placeholder(R.drawable.placeholder_avatar)
                        .into(avatarView)
                    avatarStringUri = neutralPose
                }
            }
        }
    }

    // Uploads all local outfit pose images and avatar, then saves persona
    private fun uploadAllPosesAndAvatarAndSavePersona(persona: PersonaProfile) {
        val userId = persona.author
        val personaId = persona.id
        val storage = FirebaseStorage.getInstance().reference

        // Gather pose image upload tasks
        val poseTasks = persona.outfits.flatMap { outfit ->
            outfit.poseSlots.mapNotNull { poseSlot ->
                val poseName = poseSlot.name.trim()
                val uriStr = poseSlot.uri?.trim() ?: ""
                if (poseName.isBlank() || uriStr.isBlank() || uriStr.startsWith("http")) return@mapNotNull null
                val fileUri = Uri.parse(uriStr)
                val ext = contentResolver.getType(fileUri)
                    ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                    ?: "jpg"
                val ref = storage.child("users/$userId/personas/$personaId/outfits/${outfit.name}/$poseName.$ext")
                Log.d("PersonaCreation", "Uploading $fileUri to $ref")
                val uploadTask = ref.putFile(fileUri)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        ref.downloadUrl
                    }
                    .continueWith { t ->
                        Triple(outfit.name, poseName, t.result.toString())
                    }
                uploadTask
            }
        }

        // Avatar upload (if local)
        val avatarUploadTask =
            if (avatarUri != null && avatarUri.toString().startsWith("content://")) {
                val ext = contentResolver.getType(avatarUri!!)
                    ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                    ?: "jpg"
                val avatarRef = storage.child("users/$userId/personas/$personaId/avatar.$ext")
                avatarRef.putFile(avatarUri!!)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception!!
                        avatarRef.downloadUrl
                    }
                    .continueWith { t ->
                        Triple("__avatar", "__avatar", t.result.toString())
                    }
            } else null

        val allTasks = poseTasks.toMutableList()
        avatarUploadTask?.let { allTasks += it }

        com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(allTasks)
            .addOnSuccessListener { results ->
                val poseTriples = results.filterIsInstance<Triple<String, String, String>>()
                // Map of outfitName -> MutableMap<poseName, url>
                val outfitMap = mutableMapOf<String, MutableMap<String, String>>()
                var avatarUrl: String? = null

                for ((outfitName, poseName, url) in poseTriples) {
                    if (outfitName == "__avatar" && poseName == "__avatar") {
                        avatarUrl = url
                    } else {
                        val poseMap = outfitMap.getOrPut(outfitName) { mutableMapOf() }
                        poseMap[poseName] = url
                    }
                }

                // Build updated outfit list with remote pose URLs
                val updatedOutfits = persona.outfits.map { outfit ->
                    val updatedPoseSlots = outfit.poseSlots.map { poseSlot ->
                        val newUrl = outfitMap[outfit.name]?.get(poseSlot.name) ?: poseSlot.uri
                        poseSlot.copy(uri = newUrl)
                    }.toMutableList()
                    outfit.copy(poseSlots = updatedPoseSlots)
                }

                val finalPersona = persona.copy(
                    outfits = updatedOutfits,
                    avatarUri = avatarUrl ?: persona.avatarUri
                )
                savePersona(finalPersona)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun savePersona(persona: PersonaProfile) {
        FirebaseFirestore.getInstance()
            .collection("personas")
            .document(persona.id)
            .set(persona)
            .addOnSuccessListener {
                Toast.makeText(this, "Persona saved!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving persona: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
