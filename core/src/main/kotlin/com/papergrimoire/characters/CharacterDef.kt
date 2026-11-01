package com.papergrimoire.characters

import com.papergrimoire.board.TileType

/** Static definition of a playable character (data-driven catalogue entry). */
data class CharacterDef(
    val id: String,
    val name: String,
    val title: String,
    val bio: String,
    val maxHp: Int,
    val baseMoves: Int,
    val attackPower: Int,          // multiplies SHARD match hits
    val puzzlePower: Int,          // generic bonus damage on all match families
    val luck: Int,                 // influences heart/loot rolls
    val passiveId: String,         // refers to PassiveCatalogue
    val startingSpells: List<String>,
    val startingItems: List<String> = emptyList(),
    val unlockHint: String? = null, // null => available from the start
    val colorHex: Long,             // paper doll accent colour
)

object CharacterCatalogue {
    val all: List<CharacterDef> = listOf(
        CharacterDef(
            id = "mortimer",
            name = "Mortimer Quill",
            title = "The Ink-Stained Wretch",
            bio = "A disgraced court scribe who drank too much cursed ink. His blood now " +
                  "runs red like shattered garnet — every shard he bleeds cuts someone else.",
            maxHp = 70, baseMoves = 3, attackPower = 4, puzzlePower = 1, luck = 5,
            passiveId = "shard_blood",
            startingSpells = listOf("ink_splash", "hasty_nib"),
            colorHex = 0xC9403BFF,
        ),
        CharacterDef(
            id = "bretta",
            name = "Bretta Moldwright",
            title = "The Kiln Widow",
            bio = "She buries her grief under wet clay and fires it into walls nobody can " +
                  "break. Grown folk call her stubborn; dungeon floors call her mercy.",
            maxHp = 95, baseMoves = 2, attackPower = 2, puzzlePower = 0, luck = 3,
            passiveId = "wet_clay",
            startingSpells = listOf("clay_wall", "mending_slurry"),
            colorHex = 0x5F9E58FF,
        ),
        CharacterDef(
            id = "fitch",
            name = "Fitch Mildew",
            title = "The Spore Sommelier",
            bio = "A former cellar-taster who inhaled the wrong vintage. Now mushrooms grow " +
                  "in his ears and whisper the bad habits of every monster he meets.",
            maxHp = 60, baseMoves = 3, attackPower = 2, puzzlePower = 1, luck = 8,
            passiveId = "spore_network",
            startingSpells = listOf("sleep_cap", "dust_puff"),
            colorHex = 0x9B59B6FF,
        ),
        CharacterDef(
            id = "ozoline",
            name = "Ozoline Tinspark",
            title = "The Coiled Tinker",
            bio = "Half clockwork tinker, half static shock. She never stops moving — the " +
                  "world simply hasn't caught up with her hands yet.",
            maxHp = 55, baseMoves = 4, attackPower = 2, puzzlePower = 0, luck = 6,
            passiveId = "overclock",
            startingSpells = listOf("spark_wind", "rewind"),
            colorHex = 0xE8C33DFF,
        ),
        CharacterDef(
            id = "garrick",
            name = "Garrick Deepvein",
            title = "The Crystal Eater",
            bio = "A mine-golem hunter who gnaws quartz for breakfast. Fewer moves, but each " +
                  "crystal he cracks brings the mountain down on someone's head.",
            maxHp = 80, baseMoves = 2, attackPower = 3, puzzlePower = 2, luck = 2,
            passiveId = "deep_resonance",
            startingSpells = listOf("crystal_lance", "geode_shell"),
            colorHex = 0x3E8FD0FF,
        ),
        CharacterDef(
            id = "vespera",
            name = "Vespera Hexquill",
            title = "The Margin Witch",
            bio = "She writes spells in the margins of reality and dares the universe to " +
                  "edit her. Curses bloom wherever she spills tea. Unlocked by victory.",
            maxHp = 62, baseMoves = 3, attackPower = 3, puzzlePower = 1, luck = 9,
            passiveId = "margin_magic",
            startingSpells = listOf("curse_ward", "hex_bolt"),
            startingItems = listOf("cursed_quill"),
            unlockHint = "Win a run with any hero",
            colorHex = 0x7D5BA6FF,
        ),
    )

    fun byId(id: String): CharacterDef = all.first { it.id == id }
}
