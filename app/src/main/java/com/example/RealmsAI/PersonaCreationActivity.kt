package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.RealmsAI.models.PersonaProfile
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

class PersonaCreationActivity : AppCompatActivity() {
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
    private lateinit var saveBtn: Button

    private var avatarUri: Uri? = null
    private var wardrobeImages: List<String> = emptyList()
    private var avatarStringUri: String = ""

    companion object {
        const val WARDROBE_REQUEST = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_persona)

        // Fields
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
        saveBtn   = findViewById(R.id.savePersonaButton)

        // Avatar picker
        val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                avatarUri = it
                avatarStringUri = it.toString()
                avatarView.setImageURI(it)
            }
        }
        avatarView.setOnClickListener { avatarPicker.launch("image/*") }

        // Wardrobe picker
        wardrobeBtn.setOnClickListener {
            val intent = Intent(this, WardrobeActivity::class.java)
            startActivityForResult(intent, WARDROBE_REQUEST)
        }
        val ageStr = ageEt.text.toString().trim()
        val age = ageStr.toIntOrNull() ?: 0 // or any sensible default/validation
        // Save
        saveBtn.setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                Toast.makeText(this, "You must be logged in to save a persona.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val persona = PersonaProfile(
                id          = System.currentTimeMillis().toString(),
                name        = nameEt.text.toString().trim(),
                age         = age,
                gender      = genderEt.text.toString().trim(),
                height      = heightEt.text.toString().trim(),
                hair        = hairEt.text.toString().trim(),
                eyes        = eyesEt.text.toString().trim(),
                description = descEt.text.toString().trim(),
                images      = wardrobeImages,
                author      = userId,
                avatarUri   = avatarStringUri
            )
            uploadAvatarAndSavePersona(persona)
            savePersona(persona)
        }
    }

    private fun savePersona(persona: PersonaProfile) {
        // Write to Firestore (under user collection for isolation)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WARDROBE_REQUEST && resultCode == Activity.RESULT_OK) {
            val outfitsJson = data?.getStringExtra(WardrobeActivity.EXTRA_OUTFITS_JSON)
            if (!outfitsJson.isNullOrEmpty()) {
                // Parse outfits JSON and extract images for this persona
                val outfits = Gson().fromJson(outfitsJson, Array<com.example.RealmsAI.models.Outfit>::class.java)
                wardrobeImages = outfits.flatMap { it.poseUris.values }
                // Optionally show preview of the first image as avatar
                wardrobeImages.firstOrNull()?.let {
                    avatarView.setImageURI(Uri.parse(it))
                    avatarStringUri = it // set as persona's avatar if you want
                }
            }
        }
    }
    private fun uploadAvatarAndSavePersona(persona: PersonaProfile) {
        val userId = persona.author
        val personaId = persona.id
        val storageRef = FirebaseStorage.getInstance().reference
        val avatarUri = avatarUri // the Uri picked by user

        if (avatarUri != null) {
            val ext = contentResolver.getType(avatarUri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: "jpg"
            val avatarRef = storageRef.child("users/$userId/personas/$personaId/avatar.$ext")

            avatarRef.putFile(avatarUri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception!!
                    avatarRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    val updatedPersona = persona.copy(avatarUri = downloadUri.toString())
                    savePersona(updatedPersona) // Save persona with Storage URL
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Avatar upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            savePersona(persona) // No avatar, just save persona data
        }
    }

}
