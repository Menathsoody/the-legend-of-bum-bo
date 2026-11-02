package com.papergrimoire.enemies

import com.papergrimoire.board.TileType
import com.papergrimoire.combat.StatusKind

/**
 * Full enemy + boss catalogue. 34 regular enemies and 10 bosses across 4 areas.
 * Behaviour variety: attackers, defenders, summoners, board-manipulators, buffer
 * supports, thieves, poisoners and telegraphed heavy hitters.
 */
object EnemyCatalogue {

    private fun A(dmg: Int, st: StatusKind? = null, sv: Int = 0) = EnemyAction.Attack(dmg, st, sv)
    private val EMPTY_AREAS = listOf("cellar", "bonefen", "clockwork", "grimoire")

    // ------------------------------------------------------------------ AREA 1: Damp Cellar
    val cellarEnemies = listOf(
        EnemyDef("ratling", "Cellar Ratling", hp = 12, areaIds = listOf("cellar"), moveWeight = 6,
            script = listOf(A(4)), goldMin = 1, goldMax = 3, colorHex = 0xA9825EFF,
            flavor = "Gnaws corks and hopes."),
        EnemyDef("moldbeetle", "Mold Beetle", hp = 18, areaIds = listOf("cellar"), moveWeight = 5,
            script = listOf(EnemyAction.Defend, A(3)), goldMin = 2, goldMax = 4, colorHex = 0x6B8E23FF,
            flavor = "Armoured in last year's jam."),
        EnemyDef("gremlin_scribe", "Ink Gremlin", hp = 14, areaIds = listOf("cellar"), moveWeight = 4,
            script = listOf(A(3), EnemyAction.BoardCurse(1)), unpredictability = 20,
            goldMin = 2, goldMax = 5, colorHex = 0x444444FF,
            flavor = "Spills ink where you step."),
        EnemyDef("caskling", "Caskling", hp = 22, areaIds = listOf("cellar"), moveWeight = 4,
            script = listOf(A(5, StatusKind.BLEED, 2), EnemyAction.GainShield(4)),
            goldMin = 2, goldMax = 5, colorHex = 0x8B5A2BFF),
        EnemyDef("cobwebweaver", "Cobweb Weaver", hp = 16, areaIds = listOf("cellar"), moveWeight = 3,
            script = listOf(EnemyAction.ApplyToPlayer(StatusKind.HEAL_BLOCK, 0, 2), A(2)),
            goldMin = 2, goldMax = 6, colorHex = 0xDDDDDDFF,
            flavor = "Stitches your wounds shut — wrong way."),
        EnemyDef("fermaworm", "Ferma Worm", hp = 10, areaIds = listOf("cellar"), moveWeight = 5,
            script = listOf(EnemyAction.PoisonCloud(2), A(2)), goldMin = 1, goldMax = 4,
            colorHex = 0xB8860BFF),
        EnemyDef("jar_golem_min", "Cracked Jar Golem", hp = 26, areaIds = listOf("cellar"), moveWeight = 3,
            script = listOf(EnemyAction.GainShield(6), A(6), EnemyAction.DoNothing),
            goldMin = 3, goldMax = 6, colorHex = 0xCD853FFF),
        EnemyDef("picklehead", "Picklehead Brute", hp = 24, areaIds = listOf("cellar"), moveWeight = 3,
            script = listOf(A(7), EnemyAction.HeavyAttack(11)), unpredictability = 30,
            goldMin = 2, goldMax = 6, colorHex = 0x9ACD32FF),
        EnemyDef("mousethef", "Coat Mouse", hp = 13, areaIds = listOf("cellar"), moveWeight = 4,
            script = listOf(EnemyAction.Steal(3), A(2)), goldMin = 1, goldMax = 8,
            colorHex = 0xC0C0C0FF, flavor = "Steals buttons and coins alike."),
        EnemyDef("sporepuff", "Spore Puff", hp = 11, areaIds = listOf("cellar"), moveWeight = 4,
            script = listOf(EnemyAction.BoardSeal, A(2, StatusKind.POISON, 1)),
            goldMin = 2, goldMax = 4, colorHex = 0xBA55D3FF),
        EnemyDef("tunnelrat_pack", "Twin Tunnelrats", hp = 20, areaIds = listOf("cellar"), moveWeight = 3,
            script = listOf(A(3), EnemyAction.Summon("ratling")), goldMin = 2, goldMax = 5,
            colorHex = 0x8B7355FF),
        EnemyDef("brine_shambler", "Brine Shambler", hp = 30, areaIds = listOf("cellar"), moveWeight = 2,
            script = listOf(A(4), A(4), EnemyAction.ApplyToPlayer(StatusKind.WEAKENED, 25, 2)),
            goldMin = 3, goldMax = 7, colorHex = 0x5F9EA0FF),
        EnemyDef("lantern_moth", "Lantern Moth", hp = 9, areaIds = listOf("cellar", "bonefen"), moveWeight = 3,
            script = listOf(EnemyAction.BuffAlly(20), A(1)), goldMin = 2, goldMax = 4,
            colorHex = 0xFFD700FF, flavor = "Support light unit."),
        EnemyDef("cork_imp", "Cork Imp", hp = 15, areaIds = listOf("cellar", "bonefen"), moveWeight = 4,
            script = listOf(EnemyAction.BoardMark, A(3)), unpredictability = 25,
            goldMin = 2, goldMax = 5, colorHex = 0xFF7F50FF),
    )

    // ------------------------------------------------------------------ AREA 2: Bone Fen
    val bonefenEnemies = listOf(
        EnemyDef("bogskel", "Bog Skel", hp = 22, areaIds = listOf("bonefen"), moveWeight = 5,
            script = listOf(A(6), EnemyAction.GainShield(3)), goldMin = 3, goldMax = 6,
            colorHex = 0xF5F5DCFF),
        EnemyDef("marshwisp", "Marsh Wisp", hp = 14, areaIds = listOf("bonefen"), moveWeight = 4,
            script = listOf(EnemyAction.ConvertTiles(TileType.CURSE, 2), A(3)),
            goldMin = 2, goldMax = 6, colorHex = 0x7FFFD4FF),
        EnemyDef("fen_hound", "Fen Hound", hp = 26, areaIds = listOf("bonefen"), moveWeight = 4,
            script = listOf(A(5), A(5, StatusKind.BLEED, 3)), goldMin = 3, goldMax = 6,
            colorHex = 0x8B4513FF),
        EnemyDef("gravedoll", "Grave Doll", hp = 18, areaIds = listOf("bonefen"), moveWeight = 3,
            script = listOf(EnemyAction.Summon("bogskel"), A(2)), unpredictability = 20,
            goldMin = 3, goldMax = 7, colorHex = 0xDEB887FF),
        EnemyDef("toadcantor", "Toad Cantor", hp = 20, areaIds = listOf("bonefen"), moveWeight = 3,
            script = listOf(EnemyAction.BuffAlly(30), A(2), EnemyAction.SleepMist),
            goldMin = 3, goldMax = 6, colorHex = 0x2E8B57FF),
        EnemyDef("ribcage_crab", "Ribcage Crab", hp = 34, areaIds = listOf("bonefen"), moveWeight = 3,
            script = listOf(EnemyAction.GainShield(8), A(4), A(4)), goldMin = 3, goldMax = 7,
            colorHex = 0xBC8F8FFF),
        EnemyDef("leechmage", "Leech Mage", hp = 24, areaIds = listOf("bonefen"), moveWeight = 3,
            script = listOf(A(6), EnemyAction.HealSelf(6)), goldMin = 4, goldMax = 7,
            colorHex = 0x800000FF, flavor = "Drinks what it deals."),
        EnemyDef("bogmummy", "Bog Mummy", hp = 40, areaIds = listOf("bonefen"), moveWeight = 2,
            script = listOf(EnemyAction.ApplyToPlayer(StatusKind.HEAL_BLOCK, 0, 3), A(3), A(3)),
            goldMin = 4, goldMax = 8, colorHex = 0xD2B48CFF),
        EnemyDef("stork_bandit", "Bone Stork Bandit", hp = 21, areaIds = listOf("bonefen"), moveWeight = 4,
            script = listOf(EnemyAction.Steal(6), A(4)), unpredictability = 30,
            goldMin = 2, goldMax = 10, colorHex = 0xFFFFF0FF),
        EnemyDef("heron_knight", "Heron Knight", hp = 30, areaIds = listOf("bonefen"), moveWeight = 2,
            script = listOf(EnemyAction.HeavyAttack(12), A(4), EnemyAction.GainShield(5)),
            goldMin = 4, goldMax = 8, colorHex = 0x778899FF),
        EnemyDef("spore_zombie", "Spore Zombie", hp = 28, areaIds = listOf("bonefen", "cellar"), moveWeight = 3,
            script = listOf(A(4, StatusKind.POISON, 2), A(4, StatusKind.POISON, 2)),
            goldMin = 3, goldMax = 6, colorHex = 0x6B8E23FF),
        EnemyDef("willow_witch", "Willow Witch", hp = 23, areaIds = listOf("bonefen"), moveWeight = 2,
            script = listOf(EnemyAction.BoardCurse(3), EnemyAction.ApplyToPlayer(StatusKind.VULNERABLE, 25, 2)),
            unpredictability = 25, goldMin = 4, goldMax = 8, colorHex = 0x9932CCFF),
        EnemyDef("catfish_sage", "Catfish Sage", hp = 19, areaIds = listOf("bonefen"), moveWeight = 3,
            script = listOf(EnemyAction.ShiftBoardRow(1, 2), A(3), EnemyAction.HealSelf(4)),
            goldMin = 3, goldMax = 6, colorHex = 0x4682B4FF),
    )

    // ------------------------------------------------------------------ AREA 3: Clockworks
    val clockworkEnemies = listOf(
        EnemyDef("rustbot", "Rust Bot", hp = 30, areaIds = listOf("clockwork"), moveWeight = 5,
            script = listOf(A(7), EnemyAction.GainShield(4)), goldMin = 4, goldMax = 7,
            colorHex = 0xB8860BFF),
        EnemyDef("springlasher", "Spring Lasher", hp = 26, areaIds = listOf("clockwork"), moveWeight = 4,
            script = listOf(A(4), A(4), A(4)), goldMin = 4, goldMax = 7, colorHex = 0xC0C0C0FF),
        EnemyDef("geargnome", "Gear Gnome", hp = 22, areaIds = listOf("clockwork"), moveWeight = 4,
            script = listOf(EnemyAction.Summon("rustbot"), A(3)), goldMin = 3, goldMax = 8,
            colorHex = 0x228B22FF),
        EnemyDef("boilerfiend", "Boiler Fiend", hp = 35, areaIds = listOf("clockwork"), moveWeight = 3,
            script = listOf(EnemyAction.HeavyAttack(16), A(5, StatusKind.BLEED, 3)),
            goldMin = 4, goldMax = 8, colorHex = 0xFF4500FF),
        EnemyDef("conduit_cult", "Conduit Cultist", hp = 24, areaIds = listOf("clockwork"), moveWeight = 3,
            script = listOf(EnemyAction.BuffAlly(35), A(3), EnemyAction.BoardMark),
            goldMin = 4, goldMax = 7, colorHex = 0x4169E1FF),
        EnemyDef("pressgolem", "Stamping Press Golem", hp = 48, areaIds = listOf("clockwork"), moveWeight = 2,
            script = listOf(EnemyAction.GainShield(10), EnemyAction.HeavyAttack(14), A(6)),
            goldMin = 5, goldMax = 9, colorHex = 0x696969FF),
        EnemyDef("wireimp", "Wire Imp", hp = 18, areaIds = listOf("clockwork"), moveWeight = 4,
            script = listOf(EnemyAction.BoardMark, EnemyAction.BoardMark, A(3)), unpredictability = 30,
            goldMin = 3, goldMax = 7, colorHex = 0xFFD700FF),
        EnemyDef("pendulum", "Pendulum Blade", hp = 27, areaIds = listOf("clockwork"), moveWeight = 3,
            script = listOf(EnemyAction.ShiftBoardRow(2, -3), A(8)), goldMin = 4, goldMax = 7,
            colorHex = 0xDAA520FF),
        EnemyDef("steamherald", "Steam Herald", hp = 32, areaIds = listOf("clockwork"), moveWeight = 2,
            script = listOf(EnemyAction.ApplyToPlayer(StatusKind.WEAKENED, 30, 3), A(5), EnemyAction.BuffAlly(20)),
            goldMin = 4, goldMax = 9, colorHex = 0xF0F8FFFF),
        EnemyDef("cogswarm", "Cog Swarm", hp = 16, areaIds = listOf("clockwork"), moveWeight = 4,
            script = listOf(A(2), EnemyAction.Summon("rustbot")), goldMin = 2, goldMax = 6,
            colorHex = 0xA9A9A9FF),
        EnemyDef("lampbearer", "Lampbearer Construct", hp = 29, areaIds = listOf("clockwork", "grimoire"), moveWeight = 3,
            script = listOf(EnemyAction.HealSelf(8), A(4)), goldMin = 4, goldMax = 7,
            colorHex = 0xFFFACDFF),
        EnemyDef("shutterservant", "Shutter Servant", hp = 25, areaIds = listOf("clockwork"), moveWeight = 3,
            script = listOf(EnemyAction.BoardSeal, EnemyAction.BoardSeal, A(4)),
            goldMin = 3, goldMax = 8, colorHex = 0x2F4F4FFF),
    )

    // ------------------------------------------------------------------ AREA 4: The Grimoire Vault
    val grimoireEnemies = listOf(
        EnemyDef("pageghoul", "Page Ghoul", hp = 34, areaIds = listOf("grimoire"), moveWeight = 4,
            script = listOf(A(8), EnemyAction.BoardCurse(2)), goldMin = 5, goldMax = 8,
            colorHex = 0xF0E68CFF),
        EnemyDef("redmarker", "Red Marker", hp = 30, areaIds = listOf("grimoire"), moveWeight = 4,
            script = listOf(EnemyAction.HeavyAttack(18), A(6)), goldMin = 5, goldMax = 8,
            colorHex = 0xDC143CFF),
        EnemyDef("indexwraith", "Index Wraith", hp = 28, areaIds = listOf("grimoire"), moveWeight = 3,
            script = listOf(EnemyAction.Summon("pageghoul"), EnemyAction.Steal(8)), unpredictability = 30,
            goldMin = 4, goldMax = 10, colorHex = 0x483D8BFF),
        EnemyDef("inkvine", "Inkvine", hp = 40, areaIds = listOf("grimoire"), moveWeight = 3,
            script = listOf(EnemyAction.ApplyToPlayer(StatusKind.HEAL_BLOCK, 0, 3), A(6, StatusKind.POISON, 3)),
            goldMin = 5, goldMax = 9, colorHex = 0x006400FF),
        EnemyDef("footnote_knight", "Footnote Paladin", hp = 46, areaIds = listOf("grimoire"), moveWeight = 2,
            script = listOf(EnemyAction.GainShield(12), A(9), EnemyAction.HeavyAttack(15)),
            goldMin = 6, goldMax = 10, colorHex = 0xB0C4DEFF),
        EnemyDef("errata_swarm", "Errata Swarm", hp = 22, areaIds = listOf("grimoire"), moveWeight = 3,
            script = listOf(EnemyAction.ConvertTiles(TileType.CURSE, 3), A(3)),
            goldMin = 4, goldMax = 8, colorHex = 0xFF1493FF),
        EnemyDef("bookwyrm", "Book Wyrm", hp = 52, areaIds = listOf("grimoire"), moveWeight = 2,
            script = listOf(A(7), A(7, StatusKind.BLEED, 4), EnemyAction.HealSelf(8)),
            goldMin = 6, goldMax = 12, colorHex = 0x8FBC8FFF),
        EnemyDef("marginalia", "Marginalia Sprite", hp = 20, areaIds = listOf("grimoire"), moveWeight = 4,
            script = listOf(EnemyAction.BuffAlly(40), EnemyAction.BoardMark, A(2)),
            goldMin = 4, goldMax = 7, colorHex = 0xEE82EEFF),
    )

    // ------------------------------------------------------------------ BOSSES (10)
    val bosses = listOf(
        EnemyDef("boss_jamfather", "The Jam Father", hp = 120, isBoss = true,
            areaIds = listOf("cellar"), script = listOf(
                A(8), EnemyAction.GainShield(10), EnemyAction.BoardCurse(3), A(10, StatusKind.BLEED, 3),
            ), phases = listOf(
                listOf(A(8), EnemyAction.GainShield(10), EnemyAction.BoardCurse(3)),
                listOf(A(10, StatusKind.BLEED, 3), EnemyAction.Summon("caskling"), EnemyAction.HeavyAttack(18)),
            ),
            goldMin = 25, goldMax = 40, colorHex = 0xB22222FF,
            flavor = "Baron of preserved fruit; summons barrels when losing."),
        EnemyDef("boss_heretic_toad", "Heretic Toad", hp = 140, isBoss = true,
            areaIds = listOf("cellar", "bonefen"), script = listOf(
                EnemyAction.SleepMist, A(9), EnemyAction.PoisonCloud(4),
            ), phases = listOf(
                listOf(EnemyAction.SleepMist, A(9), EnemyAction.PoisonCloud(4)),
                listOf(EnemyAction.HealSelf(15), EnemyAction.BuffAlly(30), A(12)),
            ),
            goldMin = 28, goldMax = 45, colorHex = 0x2F4F2FFF),
        EnemyDef("boss_bonecountess", "The Bone Countess", hp = 160, isBoss = true,
            areaIds = listOf("bonefen"), script = listOf(
                EnemyAction.Summon("bogskel"), A(10), EnemyAction.GainShield(12),
            ), phases = listOf(
                listOf(EnemyAction.Summon("bogskel"), A(10), EnemyAction.GainShield(12)),
                listOf(EnemyAction.Summon("gravedoll"), EnemyAction.HeavyAttack(20), A(8, StatusKind.BLEED, 4)),
            ),
            goldMin = 30, goldMax = 50, colorHex = 0xFAEBD7FF),
        EnemyDef("boss_leviathan_lint", "Leviathan of Lint", hp = 175, isBoss = true,
            areaIds = listOf("bonefen"), script = listOf(
                A(12), EnemyAction.ShiftBoardRow(0, 3), EnemyAction.BoardSeal,
            ), phases = listOf(
                listOf(A(12), EnemyAction.ShiftBoardRow(0, 3), EnemyAction.BoardSeal),
                listOf(EnemyAction.HeavyAttack(22), EnemyAction.ConvertTiles(TileType.CURSE, 4), A(10)),
            ),
            goldMin = 32, goldMax = 52, colorHex = 0x708090FF),
        EnemyDef("boss_foreman", "The Copper Foreman", hp = 190, isBoss = true,
            areaIds = listOf("clockwork"), script = listOf(
                EnemyAction.GainShield(15), A(11), EnemyAction.Summon("rustbot"),
            ), phases = listOf(
                listOf(EnemyAction.GainShield(15), A(11), EnemyAction.Summon("rustbot")),
                listOf(EnemyAction.HeavyAttack(24), EnemyAction.BuffAlly(40), A(13)),
            ),
            goldMin = 35, goldMax = 55, colorHex = 0xB87333FF),
        EnemyDef("boss_grand_pendulum", "Grand Pendulum", hp = 200, isBoss = true,
            areaIds = listOf("clockwork"), script = listOf(
                EnemyAction.ShiftBoardRow(1, 4), A(10), EnemyAction.BoardMark,
            ), phases = listOf(
                listOf(EnemyAction.ShiftBoardRow(1, 4), A(10), EnemyAction.BoardMark),
                listOf(EnemyAction.ShiftBoardRow(2, -4), EnemyAction.HeavyAttack(26), EnemyAction.BoardCurse(4)),
            ),
            goldMin = 35, goldMax = 55, colorHex = 0xFFD700FF),
        EnemyDef("boss_steam_tsar", "The Steam Tsar", hp = 215, isBoss = true,
            areaIds = listOf("clockwork", "grimoire"), script = listOf(
                A(12, StatusKind.BLEED, 4), EnemyAction.Summon("boilerfiend"), EnemyAction.GainShield(18),
            ), phases = listOf(
                listOf(A(12, StatusKind.BLEED, 4), EnemyAction.Summon("boilerfiend")),
                listOf(EnemyAction.HeavyAttack(28), EnemyAction.PoisonCloud(6), A(14)),
            ),
            goldMin = 38, goldMax = 60, colorHex = 0xF0FFFFCF),
        EnemyDef("boss_librarian", "The Blind Librarian", hp = 230, isBoss = true,
            areaIds = listOf("grimoire"), script = listOf(
                EnemyAction.BoardCurse(5), A(12), EnemyAction.ApplyToPlayer(StatusKind.HEAL_BLOCK, 0, 3),
            ), phases = listOf(
                listOf(EnemyAction.BoardCurse(5), A(12)),
                listOf(EnemyAction.Summon("pageghoul"), EnemyAction.HeavyAttack(25), A(12)),
            ),
            goldMin = 40, goldMax = 65, colorHex = 0xF5F5DCFF),
        EnemyDef("boss_rededitor", "The Red Editor", hp = 245, isBoss = true,
            areaIds = listOf("grimoire"), script = listOf(
                EnemyAction.ConvertTiles(TileType.CURSE, 4), A(13), EnemyAction.BoardSeal,
            ), phases = listOf(
                listOf(EnemyAction.ConvertTiles(TileType.CURSE, 4), A(13)),
                listOf(EnemyAction.BoardMark, EnemyAction.HeavyAttack(30), EnemyAction.Summon("redmarker")),
            ),
            goldMin = 42, goldMax = 68, colorHex = 0xDC143CFF),
        EnemyDef("boss_firstpen", "The First Pen", hp = 280, isBoss = true,
            areaIds = listOf("grimoire"), script = listOf(
                A(14), EnemyAction.Summon("indexwraith"), EnemyAction.GainShield(20),
            ), phases = listOf(
                listOf(A(14), EnemyAction.Summon("indexwraith")),
                listOf(EnemyAction.BoardCurse(6), EnemyAction.HeavyAttack(32), A(14, StatusKind.BLEED, 5)),
                listOf(EnemyAction.BuffAlly(50), EnemyAction.HeavyAttack(36), EnemyAction.Summon("bookwyrm")),
            ),
            goldMin = 60, goldMax = 90, colorHex = 0x2E0854FF,
            flavor = "The hand that wrote every dungeon. Final adversary."),
    )

    val allRegulars: List<EnemyDef> = cellarEnemies + bonefenEnemies + clockworkEnemies + grimoireEnemies
    val all: List<EnemyDef> = allRegulars + bosses

    private val byIdMap = all.associateBy { it.id }
    fun byId(id: String): EnemyDef? = byIdMap[id]

    /** Validate at class-load so content mistakes fail fast in tests. */
    init {
        require(allRegulars.size >= 30) { "need >=30 regular enemies, got ${allRegulars.size}" }
        require(bosses.size >= 10) { "need >=10 bosses, got ${bosses.size}" }
        require(all.map { it.id }.distinct().size == all.size) { "duplicate enemy ids" }
    }
}
