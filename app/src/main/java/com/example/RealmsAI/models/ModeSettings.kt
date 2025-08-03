package com.example.RealmsAI.models

sealed class ModeSettings : java.io.Serializable {
    data class RPGSettings(
        var genre: RPGGenre = RPGGenre.FANTASY,
        var characters: MutableList<RPGCharacter> = mutableListOf(),
        var acts: MutableList<RPAct> = mutableListOf(),
        var currentAct: Int = 0,
        var linkedToMap: MutableMap<String, MutableList<CharacterLink>> = mutableMapOf(),
        var perspective: String = "aboveTable"
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
        val mainCharId: String? = null,
        val monogamyEnabled: Boolean = false,
        val monogamyLevel: Int? = null,
        val jealousyEnabled: Boolean = false,
        val characterBoards: MutableMap<String, MutableMap<String, VNRelationship>> = mutableMapOf()
        // Map<fromCharacterId, Map<toCharacterId, RelationshipLevelDraft>>
    )

    data class VNRelationship(
        val fromId: String,
        val toId: String,
        var notes: String = "",
        var levels: MutableList<RelationshipLevel> = mutableListOf(),
        var currentLevel: Int = 0,
        var points: Int = 0
    )

    data class RelationshipLevel(
        var level: Int = 0,
        var threshold: Int = 0,         // XP or points to reach this level
        var personality: String = ""    // Description/flavor for this level
    )


//------------------------------------------------------GODMODE SETTINGS-----------------------------------------------------------------

    data class GodMode(
        val godMode: Boolean = true
    ) : ModeSettings()
}