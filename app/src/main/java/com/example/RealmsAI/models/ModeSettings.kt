package com.example.RealmsAI.models

sealed class ModeSettings : java.io.Serializable {
    data class RPGSettings(
        var genre: RPGGenre = RPGGenre.FANTASY,
        var characters: MutableList<RPGCharacter> = mutableListOf(),
        var acts: MutableList<RPAct> = mutableListOf(),
        var currentAct: Int = 0,
        var linkedToMap: MutableMap<String, MutableList<CharacterLink>> = mutableMapOf(),
        var perspective: String = "aboveTable",
        var gmStyle: String = GMStyle.HOST.name
    ) : ModeSettings()

    enum class RPGGenre {
        FANTASY,
        SCIFI,
        CYBERPUNK,
        WILDWEST,
        POST_APOCALYPTIC,
        HORROR,
        SUPERHERO,
        MURDER_MYSTERY
    }

    enum class GMStyle(val displayName: String, val description: String, val styleTag: String) {
        HOST(
            displayName = "Host",
            description = "The game’s host runs things directly behind the scenes.",
            styleTag = "neutral_host"
        ),
        GRIM_LORD(
            displayName = "The Grim Lord",
            description = "Dark, brooding, and deadly serious. Every action has a price.",
            styleTag = "grimdark_serious"
        ),
        ZANY_JIM(
            displayName = "Zany Jim",
            description = "Unpredictable and energetic. Loves chaos, hates logic.",
            styleTag = "cartoon_chaotic"
        ),
        BARDIC_FLAIR(
            displayName = "Bardic Flair",
            description = "Dramatic and poetic. Narration always rhymes or sings.",
            styleTag = "bardic_poetic"
        ),
        VETERAN_WARDEN(
            displayName = "Veteran Warden",
            description = "Disciplined and tactical. Favors fair challenges and logic.",
            styleTag = "military_precise"
        )
    }
    data class RPGCharacter(
        val characterId: String, // matches your character collection
        val name: String,
        var role: CharacterRole,     // GM, Hero, Sidekick, Villain, Target, etc.
        var characterClass: CharacterClass, // Warrior, Mage, etc (fantasy for now)
        val stats: CharacterStats,
        var equipment: List<String> = emptyList(),
        val hp: Int? = null,
        val maxHp: Int? = null,
        val defense: Int? = null
    )
    enum class CharacterRole {
        GM,
        HERO,
        SIDEKICK,
        VILLAIN,
        TARGET
    }
    enum class CharacterClass(val genre: RPGGenre, val mechanicalBonus: String) {
        WARRIOR(RPGGenre.FANTASY, "+2 to defense while wielding a weapon"),
        RANGER(RPGGenre.FANTASY, "+2 to ranged attacks"),
        ROGUE(RPGGenre.FANTASY, "+2 damage when performing a sneak attack"),
        MAGE(RPGGenre.FANTASY, "+2 to rolls involving Intelligence when casting spells"),
        CLERIC(RPGGenre.FANTASY, "+2 to healing rolls (magical or otherwise)")
    }
    data class CharacterStats(
        var strength: Int = 6,
        var agility: Int = 6,
        var intelligence: Int = 6,
        var charisma: Int = 6,
        var resolve: Int = 6
    ) {
        val strengthMod get() = statMod(strength)
        val agilityMod get() = statMod(agility)
        val intelligenceMod get() = statMod(intelligence)
        val charismaMod get() = statMod(charisma)
        val resolveMod get() = statMod(resolve)

        val defense get() = agility + 10
        fun hp(level: Int) = resolve + (2 * level)

        fun total(): Int = strength + agility + intelligence + charisma + resolve
        fun pointsLeft(): Int = 30 - total()

        private fun statMod(score: Int): Int = when (score) {
            1 -> -2
            2, 3 -> -1
            4, 5 -> 0
            6, 7 -> +1
            8 -> +2
            9 -> +3
            10 -> +5
            else -> 0 // default/fallback
        }
    }
    data class RPAct(
        var summary: String,
        var goal: String,
        var areaId: String // area id from your area model
    )
//--------------------------------------------------------VN SETTINGS-------------------------------------------------------------------

    data class VNSettings(
        var mainCharId: String? = null,
        var monogamyEnabled: Boolean = false,
        var monogamyLevel: Int? = null,
        var jealousyEnabled: Boolean = false,
        val characterBoards: MutableMap<String, MutableMap<String, VNRelationship>> = mutableMapOf(),
        var mainCharMode: Boolean = false
        // Map<fromCharacterId, Map<toCharacterId, RelationshipLevelDraft>>
    )

    data class VNRelationship(
        val fromId: String = "",
        val toId: String = "",
        var notes: String = "",
        var levels: MutableList<RelationshipLevel> = mutableListOf(),
        var currentLevel: Int = 0,
        var upTriggers: String = "",
        var downTriggers: String = "",
        var points: Int = 0
    )

    data class RelationshipLevel(
        var level: Int = 0,
        var threshold: Int = 0,
        var personality: String = ""
    )


//------------------------------------------------------GODMODE SETTINGS-----------------------------------------------------------------

    data class GodMode(
        val godMode: Boolean = true
    ) : ModeSettings()
}