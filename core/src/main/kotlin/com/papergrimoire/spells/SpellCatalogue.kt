package com.papergrimoire.spells

import com.papergrimoire.board.TileType
import com.papergrimoire.combat.StatusKind
import com.papergrimoire.data.Effect

/**
 * Spell catalogue. 56 spells across attack / defense / control / board / utility.
 * Each has mana costs (per family), cooldown, and a declarative effect list.
 */
data class SpellDef(
    val id: String,
    val name: String,
    val description: String,
    val cost: Map<TileType, Int>,      // mana price per colour
    val effects: List<Effect>,
    val cooldown: Int = 0,             // turns after casting before reusable
    val charges: Int = -1,             // -1 = infinite use; N = consumable-like spell
    val needsTarget: Boolean = false,  // requires tapping an enemy first
    val school: School,
) {
    enum class School { ATTACK, DEFENSE, CONTROL, BOARD, UTILITY }
}

object SpellCatalogue {
    private fun c(vararg pairs: Pair<TileType, Int>) = mapOf(*pairs)
    private val S = TileType.SHARD; private val C = TileType.CLAY
    private val P = TileType.SPORE; private val K = TileType.CRYSTAL; private val W = TileType.SPARK

    val all: List<SpellDef> = listOf(
        // ------------------------------------------------------------- ATTACK
        SpellDef("ink_splash", "Ink Splash", "Hurl a blot: 8 damage to one enemy.",
            c(S to 2), listOf(Effect.Damage(8)), needsTarget = true, school = SpellDef.School.ATTACK),
        SpellDef("hex_bolt", "Hex Bolt", "Crackling hex: 12 damage to one enemy.",
            c(S to 3, W to 1), listOf(Effect.Damage(12)), cooldown = 1, needsTarget = true, school = SpellDef.School.ATTACK),
        SpellDef("crystal_lance", "Crystal Lance", "Piercing shard: 16 damage, ignores shields.",
            c(K to 4), listOf(Effect.Damage(16)), cooldown = 2, needsTarget = true, school = SpellDef.School.ATTACK),
        SpellDef("shardstorm", "Shardstorm", "Three random hits for 6 each.",
            c(S to 4), listOf(Effect.MultiHit(3, 6)), cooldown = 1, school = SpellDef.School.ATTACK),
        SpellDef("quill_rain", "Quill Rain", "Five feather-quills: 4 damage each to random foes.",
            c(S to 5, W to 2), listOf(Effect.MultiHit(5, 4)), cooldown = 2, school = SpellDef.School.ATTACK),
        SpellDef("molten_type", "Molten Type", "Melt the page: 10 damage to ALL enemies.",
            c(S to 3, K to 3), listOf(Effect.Damage(10)), cooldown = 2, school = SpellDef.School.ATTACK),
        SpellDef("paper_cut_deep", "Deep Paper Cut", "A thin line opens wide: 7 dmg, bleed 3 for 3 turns.",
            c(S to 3), listOf(Effect.Damage(7), Effect.ApplyEnemyStatus(StatusKind.BLEED, 3, 3)),
            cooldown = 1, needsTarget = true, school = SpellDef.School.ATTACK),
        SpellDef("garnet_fang", "Garnet Fang", "Bite down: 9 damage twice on one foe.",
            c(S to 4), listOf(Effect.Damage(9, 0), Effect.Damage(9, 0)), needsTarget = true,
            cooldown = 1, school = SpellDef.School.ATTACK),
        SpellDef("prism_beam", "Prism Beam", "Focused light: 22 damage to one enemy.",
            c(K to 6), listOf(Effect.Damage(22)), cooldown = 3, needsTarget = true, school = SpellDef.School.ATTACK),
        SpellDef("geode_burst", "Geode Burst", "Every enemy eats crystal splinters for 8.",
            c(K to 4, S to 2), listOf(Effect.Damage(8)), cooldown = 2, school = SpellDef.School.ATTACK),
        SpellDef("static_arc", "Static Arc", "Chain snap: 6 damage to all, +spark charge.",
            c(W to 3), listOf(Effect.Damage(6), Effect.GainMana(W, 1)), cooldown = 2, school = SpellDef.School.ATTACK),
        SpellDef("redline", "Red Line", "Strike through: 14 dmg, target vulnerable +30% (2t).",
            c(S to 4, K to 2), listOf(Effect.Damage(14), Effect.ApplyEnemyStatus(StatusKind.VULNERABLE, 30, 2)),
            cooldown = 2, needsTarget = true, school = SpellDef.School.ATTACK),
        SpellDef("thorn_script", "Thorn Script", "Write barbs: 5 dmg to all, apply thorns 3 (2t).",
            c(P to 3, S to 2), listOf(Effect.Damage(5), Effect.ApplyPlayerStatus(StatusKind.THORNS, 3, 2)),
            cooldown = 2, school = SpellDef.School.ATTACK),
        SpellDef("detonation_margin", "Margin Detonation", "Blow up a page: 30 damage to one enemy.",
            c(K to 7, S to 3), listOf(Effect.Damage(30)), cooldown = 4, needsTarget = true, school = SpellDef.School.ATTACK),
        SpellDef("splinter Volley".replace(" ", "_"), "Splinter Volley", "Four quick jabs for 3.",
            c(S to 2), listOf(Effect.MultiHit(4, 3)), cooldown = 1, school = SpellDef.School.ATTACK),

        // ------------------------------------------------------------- DEFENSE
        SpellDef("clay_wall", "Clay Wall", "Raise 12 shield.",
            c(C to 3), listOf(Effect.GainShield(12)), school = SpellDef.School.DEFENSE),
        SpellDef("geode_shell", "Geode Shell", "Hard shell: 18 shield.",
            c(C to 4, K to 2), listOf(Effect.GainShield(18)), cooldown = 1, school = SpellDef.School.DEFENSE),
        SpellDef("mending_slurry", "Mending Slurry", "Lick the wounds: heal 10 HP.",
            c(C to 3), listOf(Effect.Heal(10)), cooldown = 1, school = SpellDef.School.DEFENSE),
        SpellDef("poultice_spore", "Spore Poultice", "Heal 8 and gain 6 shield.",
            c(P to 2, C to 2), listOf(Effect.Heal(8), Effect.GainShield(6)), cooldown = 1, school = SpellDef.School.DEFENSE),
        SpellDef("bark_hide", "Bark Hide", "Armor 4 for 3 turns.",
            c(C to 4), listOf(Effect.ApplyPlayerStatus(StatusKind.ARMOR, 4, 3)), cooldown = 2, school = SpellDef.School.DEFENSE),
        SpellDef("curse_ward", "Curse Ward", "Ward off harm: armor 6 for 2 turns, cleanse bleed.",
            c(C to 3, W to 2), listOf(Effect.ApplyPlayerStatus(StatusKind.ARMOR, 6, 2)),
            cooldown = 2, school = SpellDef.School.DEFENSE),
        SpellDef("stiffen_clay", "Stiffen Clay", "Fire the clay: shield equal to 3x current moves left.",
            c(C to 2), listOf(Effect.GainShield(9)), cooldown = 1, school = SpellDef.School.DEFENSE),
        SpellDef("wet_blanket", "Wet Blanket", "Smother fire: 10 shield, weaken all enemies 25% (2t).",
            c(C to 4), listOf(Effect.GainShield(10), Effect.ApplyEnemyStatus(StatusKind.WEAKENED, 25, 2, all = true)),
            cooldown = 2, school = SpellDef.School.DEFENSE),
        SpellDef("second_skin", "Second Skin", "+10 max HP permanently this run.",
            c(C to 5, S to 2), listOf(Effect.MaxHpUp(10)), cooldown = 3, school = SpellDef.School.DEFENSE),
        SpellDef("porcelain_prayer", "Porcelain Prayer", "Heal 16 HP.",
            c(C to 5), listOf(Effect.Heal(16)), cooldown = 2, school = SpellDef.School.DEFENSE),

        // ------------------------------------------------------------- CONTROL
        SpellDef("sleep_cap", "Sleep Cap", "Stun one enemy for 1 turn.",
            c(P to 3), listOf(Effect.ApplyEnemyStatus(StatusKind.STUN, 0, 1)), needsTarget = true,
            cooldown = 1, school = SpellDef.School.CONTROL),
        SpellDef("dust_puff", "Dust Puff", "Coughing cloud: stun ALL enemies for 1 turn.",
            c(P to 5), listOf(Effect.ApplyEnemyStatus(StatusKind.STUN, 0, 1, all = true)),
            cooldown = 3, school = SpellDef.School.CONTROL),
        SpellDef("spore_leash", "Spore Leash", "Poison one enemy (3 stacks, 3 turns).",
            c(P to 2), listOf(Effect.ApplyEnemyStatus(StatusKind.POISON, 3, 3)), needsTarget = true,
            school = SpellDef.School.CONTROL),
        SpellDef("mold_bloom", "Mold Bloom", "All enemies poisoned (2 stacks, 2 turns).",
            c(P to 4), listOf(Effect.ApplyEnemyStatus(StatusKind.POISON, 2, 2, all = true)),
            cooldown = 2, school = SpellDef.School.CONTROL),
        SpellDef("shield_crack", "Shield Crack", "One enemy takes +50% match damage (2 turns).",
            c(K to 2, P to 2), listOf(Effect.ApplyEnemyStatus(StatusKind.SHIELD_BREAK, 0, 2)),
            needsTarget = true, school = SpellDef.School.CONTROL),
        SpellDef("bad_vintage", "Bad Vintage", "Weaken one enemy 40% for 3 turns.",
            c(P to 3), listOf(Effect.ApplyEnemyStatus(StatusKind.WEAKENED, 40, 3)), needsTarget = true,
            school = SpellDef.School.CONTROL),
        SpellDef("fumbleweed", "Fumbleweed", "All enemies weakened 25% next turn.",
            c(P to 3, W to 1), listOf(Effect.ApplyEnemyStatus(StatusKind.WEAKENED, 25, 1, all = true)),
            cooldown = 2, school = SpellDef.School.CONTROL),
        SpellDef("doom_mark", "Doom Mark", "Mark one enemy: takes 20 damage in 2 turns.",
            c(P to 4, K to 2), listOf(Effect.ApplyEnemyStatus(StatusKind.DOOM, 20, 2)),
            needsTarget = true, cooldown = 2, school = SpellDef.School.CONTROL),
        SpellDef("stutter_hex", "Stutter Hex", "Cancel telegraphed heavy of target; stun 1t.",
            c(P to 3, W to 2), listOf(Effect.ApplyEnemyStatus(StatusKind.STUN, 0, 1)),
            needsTarget = true, cooldown = 2, school = SpellDef.School.CONTROL),

        // ------------------------------------------------------------- BOARD
        SpellDef("tile_sorter", "Tile Sorter", "Convert 4 random tiles to shards.",
            c(S to 2, W to 2), listOf(Effect.ConvertTiles(S, 4)), school = SpellDef.School.BOARD),
        SpellDef("kiln_fire", "Kiln Fire", "Convert 4 random tiles to clay.",
            c(C to 2, W to 2), listOf(Effect.ConvertTiles(C, 4)), school = SpellDef.School.BOARD),
        SpellDef("culture_swab", "Culture Swab", "Convert 4 random tiles to spores.",
            c(P to 2, W to 2), listOf(Effect.ConvertTiles(P, 4)), school = SpellDef.School.BOARD),
        SpellDef("facet_grinder", "Facet Grinder", "Convert 4 random tiles to crystals.",
            c(K to 2, W to 2), listOf(Effect.ConvertTiles(K, 4)), school = SpellDef.School.BOARD),
        SpellDef("spark_weave", "Spark Weave", "Convert 3 random tiles to sparks.",
            c(W to 3), listOf(Effect.ConvertTiles(W, 3)), school = SpellDef.School.BOARD),
        SpellDef("wild_page", "Wild Page", "Convert 3 random tiles into wildcards.",
            c(W to 4), listOf(Effect.ConvertTiles(TileType.WILD, 3)), cooldown = 2, school = SpellDef.School.BOARD),
        SpellDef("heart_bloom", "Heart Bloom", "Grow 2 healing hearts on the board.",
            c(P to 2, C to 2), listOf(Effect.ConvertTiles(TileType.HEART, 2)), cooldown = 2, school = SpellDef.School.BOARD),
        SpellDef("curse_eater", "Curse Eater", "Devour all curse tiles on the board.",
            c(P to 3), listOf(Effect.DestroyFamily(TileType.CURSE)), school = SpellDef.School.BOARD),
        SpellDef("unseal", "Unseal", "Reveal every sealed tile as a wildcard.",
            c(K to 3), listOf(Effect.ConvertTiles(TileType.WILD, 6)), cooldown = 2, school = SpellDef.School.BOARD),
        SpellDef("page_flip", "Page Flip", "Shift row 1 four cells right — set things up.",
            c(W to 1), listOf(Effect.ShiftRow(1, 4)), school = SpellDef.School.BOARD),
        SpellDef("shelf_slide", "Shelf Slide", "Shift row 2 four cells left.",
            c(W to 1), listOf(Effect.ShiftRow(2, -4)), school = SpellDef.School.BOARD),
        SpellDef("gravity_pocket", "Gravity Pocket", "Dump column 4 downward by 2.",
            c(K to 2), listOf(Effect.ShiftColumn(4, 2)), school = SpellDef.School.BOARD),
        SpellDef("detonate_line", "Detonate Line", "Blow up every crystal on the board for 3 dmg each.",
            c(K to 3), listOf(Effect.Damage(3)), cooldown = 1, school = SpellDef.School.BOARD),
        SpellDef("salt_the_earth", "Salt the Earth", "Scatter 3 curses onto enemies' luck.",
            c(P to 4), listOf(Effect.AddCurses(3)), school = SpellDef.School.BOARD),
        SpellDef("cascade_prime", "Cascade Prime", "Force-resolve matches now, free of charge.",
            c(W to 4, K to 2), listOf(Effect.ResolveNow), cooldown = 2, school = SpellDef.School.BOARD),
        SpellDef("rewind", "Rewind", "Pull column 0 up two rows and breathe again.",
            c(W to 2), listOf(Effect.ShiftColumn(0, -2), Effect.GainMoves(1)), cooldown = 2,
            school = SpellDef.School.BOARD),

        // ------------------------------------------------------------- UTILITY
        SpellDef("hasty_nib", "Hasty Nib", "Gain 1 extra move this turn.",
            c(W to 2), listOf(Effect.GainMoves(1)), school = SpellDef.School.UTILITY),
        SpellDef("spark_wind", "Spark Wind", "Gain 2 moves and 2 spark mana.",
            c(W to 3), listOf(Effect.GainMoves(2)), cooldown = 2, school = SpellDef.School.UTILITY),
        SpellDef("coin_trick", "Coin Trick", "Sleight of hand: +8 scrip.",
            c(W to 2), listOf(Effect.GainCoins(8)), charges = 5, school = SpellDef.School.UTILITY),
        SpellDef("mana_feast", "Mana Feast", "Drink deep: +3 of every mana colour.",
            c(S to 1, C to 1, P to 1, K to 1, W to 1), listOf(
                Effect.GainMana(S, 3), Effect.GainMana(C, 3), Effect.GainMana(P, 3),
                Effect.GainMana(K, 3), Effect.GainMana(W, 3)), cooldown = 3, school = SpellDef.School.UTILITY),
        SpellDef("sharpen_focus", "Sharpen Focus", "+2 puzzle power for this run.",
            c(K to 3, W to 2), listOf(Effect.PuzzlePowerUp(2)), cooldown = 3, school = SpellDef.School.UTILITY),
        SpellDef("borrow_time", "Borrow Time", "Take 2 extra moves now, lose 4 HP to fate.",
            c(W to 4), listOf(Effect.GainMoves(2), Effect.ApplyPlayerStatus(StatusKind.BLEED, 2, 1)),
            cooldown = 2, school = SpellDef.School.UTILITY),
        SpellDef("pocket_sun", "Pocket Sun", "Cleanse negative statuses, heal 5.",
            c(W to 3, C to 2), listOf(Effect.Heal(5)), cooldown = 2, school = SpellDef.School.UTILITY),
        SpellDef("reading_ahead", "Reading Ahead", "Peek deeper: refresh preview with fresh tiles.",
            c(W to 1), listOf(Effect.SealTiles(0)), school = SpellDef.School.UTILITY),
        SpellDef("thorn_cloak", "Thorn Cloak", "Thorns 4 for 3 turns.",
            c(P to 3, C to 1), listOf(Effect.ApplyPlayerStatus(StatusKind.THORNS, 4, 3)),
            cooldown = 2, school = SpellDef.School.UTILITY),
        SpellDef("lucky_stain", "Lucky Stain", "Luck +3 for the rest of the run.",
            c(W to 2, P to 2), listOf(Effect.GainCoins(3)), cooldown = 2, school = SpellDef.School.UTILITY),
    )

    private val byIdMap = all.associateBy { it.id }
    fun byId(id: String): SpellDef? = byIdMap[id]

    init {
        require(all.size >= 55) { "need >=55 spells, got ${all.size}" }
        require(all.map { it.id }.distinct().size == all.size) { "duplicate spell ids" }
    }
}
