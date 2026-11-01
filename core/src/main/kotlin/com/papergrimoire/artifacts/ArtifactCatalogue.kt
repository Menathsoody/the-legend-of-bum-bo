package com.papergrimoire.artifacts

import com.papergrimoire.board.TileType
import com.papergrimoire.data.Effect
import com.papergrimoire.items.Rarity

/**
 * Passive artefact catalogue — 84 artefacts. Each is a bundle of declarative effects
 * applied once when picked up (passives), plus optional hook tags the combat engine
 * reads for event-driven behaviour (e.g. "on_low_hp", "cross_bonus").
 */
data class ArtifactDef(
    val id: String,
    val name: String,
    val description: String,
    val rarity: Rarity,
    val effects: List<Effect>,
    /** special hooks interpreted by CombatEngine */
    val hooks: Set<String> = emptySet(),
)

object ArtifactCatalogue {
    private val S = TileType.SHARD; private val C = TileType.CLAY
    private val P = TileType.SPORE; private val K = TileType.CRYSTAL; private val W = TileType.SPARK
    private val HEART = TileType.HEART; private val CURSE = TileType.CURSE; private val WILD = TileType.WILD

    private fun art(id: String, name: String, desc: String, r: Rarity, vararg e: Effect, hooks: Set<String> = emptySet()) =
        ArtifactDef(id, name, desc, r, e.toList(), hooks)

    val all: List<ArtifactDef> = listOf(
        // ------- shard family
        art("whetstone_chip", "Whetstone Chip", "Shard matches hit +1 time.", Rarity.COMMON, Effect.PassiveShardHits(1)),
        art("garnet_dust", "Garnet Dust", "+20% mana from all matches.", Rarity.COMMON, Effect.PassiveManaGainPct(20)),
        art("broken_bottle", "Broken Bottle", "Shard matches deal +2 damage each.", Rarity.UNCOMMON, Effect.PuzzlePowerUp(2), hooks = setOf("shard_flat")),
        art("ink_stain_palm", "Ink-Stained Palm", "Shard matches hit +2 extra times.", Rarity.RARE, Effect.PassiveShardHits(2)),
        art("barber_mirror", "Barber's Mirror", "Shards spawn more often.", Rarity.UNCOMMON, Effect.PassiveSpawnWeight(S, 6.0)),
        art("redthread_spool", "Red Thread Spool", "+1 move at the start of every turn.", Rarity.LEGENDARY, Effect.PassiveExtraMove(1)),
        art("splinter_crown", "Splinter Crown", "Shard matches deal +4 but you take 1 bleed per turn.", Rarity.EPIC, Effect.PuzzlePowerUp(4), Effect.PassiveThorns(-1), hooks = setOf("shard_risk")),

        // ------- clay family
        art("kiln_thumb", "Kiln Thumb", "Clay gives +30% shield.", Rarity.COMMON, Effect.PassiveClayBonus(30)),
        art("wet_sponge", "Wet Sponge", "Clay gives +50% shield.", Rarity.RARE, Effect.PassiveClayBonus(50)),
        art("turtleback_lid_art", "Turtle Shell Lid", "Start every combat with 4 armor.", Rarity.UNCOMMON, Effect.PassiveArmorPerTurn(4)),
        art("potters_wheel", "Potter's Wheel", "Clay spawns more often.", Rarity.COMMON, Effect.PassiveSpawnWeight(C, 6.0)),
        art("damp_newspaper", "Damp Newspaper", "Heal 2 HP whenever you gain clay mana (approx: +shield 2).", Rarity.UNCOMMON, Effect.PassiveClayBonus(10), hooks = setOf("clay_heal")),
        art("porcelain_piggy", "Porcelain Piggy", "Clay matches refund 1 scrip each.", Rarity.RARE, Effect.PassiveLuck(1), hooks = setOf("clay_coin")),
        art("briar_gauntlet", "Briar Gauntlet", "Thorns 3 permanently.", Rarity.EPIC, Effect.PassiveThorns(3)),

        // ------- spore family
        art("mushroom_ear", "Mushroom Ear", "Spore control effects last +50% longer.", Rarity.COMMON, Effect.PassiveSporeBonus(50)),
        art("pollen_pouch", "Pollen Pouch", "Spores spawn more often.", Rarity.COMMON, Effect.PassiveSpawnWeight(P, 6.0)),
        art("ferment_jar", "Fermentation Jar", "Spore matches also poison ALL enemies 1 stack.", Rarity.RARE, hooks = setOf("spore_poison_all")),
        art("quiet_mold", "Quiet Mold", "Spore stun chance +25% on long matches.", Rarity.UNCOMMON, Effect.PassiveSporeBonus(25)),
        art("root_grappa", "Root Grappa", "+1 luck; hearts heal +2.", Rarity.UNCOMMON, Effect.PassiveLuck(1), hooks = setOf("heart_boost")),
        art("cough_drop", "Cough Drop", "Spore matches heal 1 HP per tile.", Rarity.EPIC, hooks = setOf("spore_heal")),

        // ------- crystal family
        art("facet_file_art", "Facet File", "Crystal matches deal +30% damage.", Rarity.COMMON, Effect.PassiveCrystalBonus(30)),
        art("deep_resonator", "Deep Resonator", "Crystal matches deal +60% damage.", Rarity.RARE, Effect.PassiveCrystalBonus(60)),
        art("quartz_tooth", "Quartz Tooth", "Crystals spawn more often.", Rarity.COMMON, Effect.PassiveSpawnWeight(K, 5.0)),
        art("geode_heart", "Geode Heart", "+8 max HP.", Rarity.UNCOMMON, Effect.MaxHpUp(8)),
        art("prism_shard_ring", "Prism Ring", "Every 3rd crystal match becomes a 7+ burst.", Rarity.EPIC, hooks = setOf("crystal_every3")),
        art("mine_lamp", "Mine Lamp", "Sealed tiles reveal as crystals instead.", Rarity.UNCOMMON, hooks = setOf("seal_crystal")),

        // ------- spark family
        art("wool_cap", "Wool Cap", "Spark matches give +50% more mana.", Rarity.COMMON, Effect.PassiveSparkBonus(50)),
        art("battery_acid", "Battery Acid", "Sparks spawn more often.", Rarity.COMMON, Effect.PassiveSpawnWeight(W, 5.0)),
        art("hasty_nib_art", "Hasty Nib Holder", "Every 4th spark match grants +1 move.", Rarity.RARE, hooks = setOf("spark_move_every4")),
        art("static_finger", "Static Finger", "Spell cooldowns tick down twice per turn occasionally.", Rarity.EPIC, hooks = setOf("cooldown_double")),
        art("windup_key", "Wind-Up Key", "+1 move on the first turn of every combat.", Rarity.UNCOMMON, hooks = setOf("first_turn_move")),

        // ------- wildcards & specials
        art("joker_mask", "Joker Mask", "Wildcards appear slightly more often.", Rarity.UNCOMMON, Effect.PassiveSpawnWeight(WILD, 0.6)),
        art("heart_locket", "Heart Locket", "Hearts spawn more often.", Rarity.UNCOMMON, Effect.PassiveSpawnWeight(HEART, 2.5)),
        art("salt_line", "Salt Line", "Curse tiles spawn half as often.", Rarity.COMMON, Effect.PassiveSpawnWeight(CURSE, -0.5)),
        art("exorcist_hood", "Exorcist's Hood", "Destroying curses heals 3 HP.", Rarity.RARE, hooks = setOf("curse_heal")),
        art("unsealer_pick", "Unsealer's Pick", "Sealed tiles count as wildcards while sealed.", Rarity.EPIC, hooks = setOf("seal_wild")),
        art("lucky_horseshoe_nail", "Lucky Nail", "+3 luck overall.", Rarity.UNCOMMON, Effect.PassiveLuck(3)),
        art("four_leaf_press", "Four-Leaf Press", "First heart each combat heals double.", Rarity.RARE, hooks = setOf("first_heart_double")),

        // ------- cross-match synergy artifacts
        art("cross_stitch", "Cross-Stitch Hoop", "Intersecting matches grant +1 move.", Rarity.EPIC, hooks = setOf("cross_move")),
        art("weaver_knot", "Weaver's Knot", "Intersecting matches deal +4 damage.", Rarity.RARE, hooks = setOf("cross_damage")),
        art("tangle_thread", "Tangled Thread", "Cascades beyond the first add +2 puzzle power that turn.", Rarity.EPIC, hooks = setOf("cascade_power")),
        art("domino_strip", "Domino Strip", "Matches of 6+ always grant +1 move.", Rarity.LEGENDARY, hooks = setOf("long_match_move")),
        art("frayed_edge", "Frayed Edge", "Matches of 7+ deal triple damage.", Rarity.LEGENDARY, hooks = setOf("mega_triple")),
        art("patchwork_coat", "Patchwork Coat", "+1 to every match size count for thresholds.", Rarity.EPIC, hooks = setOf("size_plus_one")),

        // ------- low HP risk/reward
        art("adrenaline_tea", "Adrenaline Tea", "Below 30% HP: +1 move each turn.", Rarity.RARE, hooks = setOf("lowhp_move")),
        art("grit_bandage", "Grit Bandage", "Below 30% HP: matches deal +4.", Rarity.RARE, hooks = setOf("lowhp_damage")),
        art("last_page", "The Last Page", "Once per combat, survive lethal damage at 1 HP.", Rarity.LEGENDARY, hooks = setOf("cheat_death")),
        art("panic_button", "Panic Button", "At <=20% HP gain 12 shield once per combat.", Rarity.EPIC, hooks = setOf("panic_shield")),
        art("stubborn_scab", "Stubborn Scab", "Bleed on you deals 1 less damage.", Rarity.COMMON, hooks = setOf("bleed_less")),

        // ------- economy
        art("piggy_script_art", "Script Piggy", "Scrip drops +25%.", Rarity.COMMON, Effect.PassiveLuck(2), hooks = setOf("coin_up")),
        art("beggars_thimble", "Beggar's Thimble", "Combat victories pay +4 scrip.", Rarity.UNCOMMON, hooks = setOf("victory_coin")),
        art("shoplifter_glove", "Shoplifter's Glove", "Shop prices -20%.", Rarity.RARE, hooks = setOf("shop_discount")),
        art("counterfeit_plate", "Counterfeit Plate", "First shop visit each area: one free item reroll.", Rarity.EPIC, hooks = setOf("free_reroll")),
        art("ledger_worm", "Ledger Worm", "Earn 1 scrip per curse destroyed.", Rarity.UNCOMMON, hooks = setOf("curse_coin")),

        // ------- spell shapers
        art("focus_lens", "Focus Lens", "Attack spells deal +3.", Rarity.UNCOMMON, hooks = setOf("spell_attack_up")),
        art("cooling_gel", "Cooling Gel", "Spell cooldowns reduced by 1 turn (min 1).", Rarity.RARE, hooks = setOf("cooldown_less")),
        art("mana_filter", "Mana Filter", "All spells cost 1 less coloured mana (min 1 total).", Rarity.EPIC, hooks = setOf("spell_cost_less")),
        art("hex_quill_nib", "Hex Quill Nib", "Control spells land +1 turn.", Rarity.UNCOMMON, hooks = setOf("control_longer")),
        art("double_signature", "Double Signature", "10% chance spells fire twice.", Rarity.LEGENDARY, hooks = setOf("spell_double")),
        art("borrowed_robe", "Borrowed Robe", "+2 puzzle power.", Rarity.UNCOMMON, Effect.PuzzlePowerUp(2)),
        art("professors_pin", "Professor's Pin", "+3 puzzle power.", Rarity.RARE, Effect.PuzzlePowerUp(3)),
        art("margin_note", "Margin Note", "Reading spells (utility) give +1 spark mana.", Rarity.COMMON, hooks = setOf("utility_spark")),

        // ------- defense artifacts
        art("cardboard_aegis", "Cardboard Aegis", "Shield you gain is +3 flat.", Rarity.COMMON, hooks = setOf("shield_flat")),
        art("riveted_collar", "Riveted Collar", "Armor 2 from the start of every fight.", Rarity.COMMON, Effect.PassiveArmorPerTurn(2)),
        art("feather_duster", "Feather Duster", "Enemy attacks sometimes fizzle (10% dodge).", Rarity.EPIC, hooks = setOf("dodge")),
        art("bulwark_biscuit", "Bulwark Biscuit", "Eat before battle: +6 shield at combat start.", Rarity.UNCOMMON, hooks = setOf("start_shield")),
        art("mirror_backplate", "Mirror Backplate", "Thorns 5.", Rarity.RARE, Effect.PassiveThorns(5)),
        art("overtime_permit", "Overtime Permit", "When out of moves, get exactly 1 more, once per turn.", Rarity.LEGENDARY, hooks = setOf("second_wind")),

        // ------- board shapers
        art("crooked_ruler", "Crooked Ruler", "Row shifts wrap two cells further than asked? No—row shifts cost nothing on even rows.", Rarity.RARE, hooks = setOf("even_row_free")),
        art("rail_grease", "Rail Grease", "Every 3rd column shift grants +1 mana of its top tile colour.", Rarity.UNCOMMON, hooks = setOf("shift_mana")),
        art("gravity_anvil", "Gravity Anvil", "After resolution, drop an extra crystal into column 4.", Rarity.EPIC, hooks = setOf("drop_crystal")),
        art("tidy_desk", "Tidy Desk", "Board starts each combat pre-sorted in pairs.", Rarity.RARE, hooks = setOf("tidy_start")),
        art("storm_compass", "Storm Compass", "First match each combat is upgraded by +1 size.", Rarity.EPIC, hooks = setOf("first_match_up")),

        // ------- misc oddballs
        art("fidget_spinner", "Fidget Spinner", "+1 move on turns divisible by 3.", Rarity.UNCOMMON, hooks = setOf("every3_move")),
        art("half_eaten_apple", "Half-Eaten Apple", "Heal 1 HP per turn.", Rarity.COMMON, hooks = setOf("regen")),
        art("sleepless_teeth", "Sleepless Teeth", "You cannot be stunned; instead lose 1 move.", Rarity.RARE, hooks = setOf("no_stun")),
        art("debt_ledger", "Debt Ledger", "Gain 2 scrip per turn, take 1 damage per turn.", Rarity.UNCOMMON, hooks = setOf("scrip_interest")),
        art("mothwing_cloak", "Mothwing Cloak", "Enemies forget you 15% of the time (skip action).", Rarity.EPIC, hooks = setOf("enemy_forget")),
        art("pocket_sundial", "Pocket Sundial", "Rest rooms heal +50% more.", Rarity.COMMON, hooks = setOf("rest_boost")),
        art("wax_seal_ring", "Wax Seal Ring", "Treasure rooms give +1 choice.", Rarity.RARE, hooks = setOf("treasure_extra")),
        art("thimblerig", "Thimblerig", "Rewards offer three choices instead of two.", Rarity.EPIC, hooks = setOf("reward_three")),
        art("inkblot_oracle", "Inkblot Oracle", "Events show their odds before you choose.", Rarity.UNCOMMON, hooks = setOf("event_odds")),
        art("paperboy_apron", "Paperboy's Apron", "+5 max HP, +1 scrip per victory.", Rarity.COMMON, Effect.MaxHpUp(5), hooks = setOf("victory_coin")),
        art("gilded_paperclip", "Gilded Paperclip", "Boss kills pay +15 scrip.", Rarity.RARE, hooks = setOf("boss_coin")),
        art("heretics_marker", "Heretic's Marker", "Convert any tile you tap once per combat (tap after shifting).", Rarity.EPIC, hooks = setOf("free_convert")),
        art("clockwork_liver", "Clockwork Liver", "Immune to poison.", Rarity.RARE, hooks = setOf("immune_poison")),
        art("cold_soul_jar", "Cold Soul Jar", "Immune to bleed.", Rarity.RARE, hooks = setOf("immune_bleed")),
        art("laughing_stockings", "Laughing Stockings", "Cleanse all debuffs at the start of each combat.", Rarity.EPIC, hooks = setOf("cleanse_start")),
        art("second_breakfast", "Second Breakfast", "First item used each combat costs no charge.", Rarity.LEGENDARY, hooks = setOf("item_free_charge")),
        art("bottomless_thimble", "Bottomless Thimble", "Items recharge 1 charge in rest rooms.", Rarity.RARE, hooks = setOf("item_recharge")),
        art("nervous_hamstring", "Nervous Hamstring", "+2 moves, -8 max HP.", Rarity.UNCOMMON, Effect.PassiveExtraMove(2), Effect.MaxHpUp(-8)),
        art("mountain_pill", "Mountain Pill", "+16 max HP, -1 move.", Rarity.UNCOMMON, Effect.MaxHpUp(16), Effect.PassiveExtraMove(-1)),
        art("greased_lightning", "Greased Lightning", "+1 move, matches deal -2.", Rarity.RARE, Effect.PassiveExtraMove(1), hooks = setOf("match_minus")),
        art("obsidian_nib", "Obsidian Nib", "Shard and crystal matches deal +2.", Rarity.EPIC, Effect.PuzzlePowerUp(2), hooks = setOf("shard_crystal_flat")),
        art("quiet_confetti", "Quiet Confetti", "Every cleared cascade bursts +1 heart chance.", Rarity.LEGENDARY, hooks = setOf("cascade_heart")),
    )

    private val byIdMap = all.associateBy { it.id }
    fun byId(id: String): ArtifactDef? = byIdMap[id]

    init {
        require(all.size >= 80) { "need >=80 artifacts, got ${all.size}" }
        require(all.map { it.id }.distinct().size == all.size) { "duplicate artifact ids" }
    }
}
