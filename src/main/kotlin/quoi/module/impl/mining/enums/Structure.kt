package quoi.module.impl.mining.enums

import quoi.api.colour.Colour
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

enum class Structure( // todo cleanup some day
    val colour: Colour,
    val blocks: List<Block?>,
    val type: StructureType,
    val quarter: CrystalHollowsQuarter,
    val displayName: String,
    val xOffset: Int,
    val yOffset: Int,
    val zOffset: Int,
    val canBeMultiple: Boolean = false
) {
    QUEEN(
        Colour.ORANGE,
        listOf( // todo fix
            Blocks.STONE,
            Blocks.ACACIA_LOG,
            Blocks.ACACIA_LOG,
            Blocks.ACACIA_LOG,
            Blocks.CAULDRON,
//            null,
            Blocks.FIRE
        ),
        StructureType.CH_CRYSTALS, CrystalHollowsQuarter.GOBLIN_HOLDOUT, "Goblin Queen", 0, 5, 0
    ),

    DIVAN(
        Colour.BLUE,
        listOf(
            Blocks.QUARTZ_PILLAR,
            Blocks.QUARTZ_STAIRS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.CHISELED_STONE_BRICKS
        ),
        StructureType.CH_CRYSTALS, CrystalHollowsQuarter.MITHRIL_DEPOSITS, "Mines of Divan", 0, 5, 0
    ),

    CITY(
        Colour.WHITE,
        listOf(
            Blocks.COBBLESTONE,
            Blocks.COBBLESTONE,
            Blocks.COBBLESTONE,
            Blocks.COBBLESTONE,
            Blocks.COBBLESTONE_STAIRS,
            Blocks.POLISHED_ANDESITE,
            Blocks.POLISHED_ANDESITE,
            Blocks.DARK_OAK_STAIRS
        ),
        StructureType.CH_CRYSTALS, CrystalHollowsQuarter.PRECURSOR_REMNANTS, "Precursor City", 24, 0, -17
    ),

    TEMPLE(
        Colour.MINECRAFT_DARK_GREEN,
        listOf(
            Blocks.BEDROCK,
            Blocks.BEDROCK,
            Blocks.CLAY,
            Blocks.CLAY,
//            Blocks.TERRACOTTA,
//            Blocks.YELLOW_WOOL, // todo figure what wool it is
//            Blocks.OAK_LEAVES,
//            Blocks.OAK_LEAVES
        ),
        StructureType.CH_CRYSTALS, CrystalHollowsQuarter.JUNGLE, "Jungle Temple", -45, 47, -18
    ),

    KING(
        Colour.MINECRAFT_YELLOW,
        listOf(
            Blocks.RED_WOOL,
            Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_STAIRS
        ),
        StructureType.CH_CRYSTALS, CrystalHollowsQuarter.GOBLIN_HOLDOUT, "Goblin King", 1, -1, 2
    ),

    BAL(
        Colour.MINECRAFT_RED,
        listOf(
            Blocks.LAVA,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER,
            Blocks.BARRIER
        ),
        StructureType.CH_CRYSTALS, CrystalHollowsQuarter.MAGMA_FIELDS, "Bal", 0, 1, 0
    ),

    FAIRY_GROTTO( // todo figure specific types of grottos
        Colour.PINK,
        listOf(
            Blocks.MAGENTA_STAINED_GLASS
        ),
        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Fairy Grotto", 0, 0, 0, canBeMultiple = true
    ),

//    MANSION_GROTTO( // todo find a diff way
//        Colour.MINECRAFT_DARK_PURPLE,
//        listOf(
//            Blocks.GREEN_WOOL,
//            Blocks.GREEN_WOOL,
//            Blocks.GREEN_WOOL,
//            Blocks.GREEN_WOOL,
//            Blocks.GREEN_WOOL,
//            Blocks.GREEN_WOOL,
//            Blocks.GREEN_WOOL,
//            Blocks.GREEN_WOOL,
//            Blocks.STONE
//        ),
//        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Mansion Grotto", 0, 0, 0, canBeMultiple = true
//    ),

    RUINS_GROTTO_1(
        Colour.PINK,
        listOf(
            Blocks.STONE,
            Blocks.LANTERN,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS_PANE,
        ),
        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Ruins Grotto 1", 0, 0, 0, canBeMultiple = true
    ),

    RUINS_GROTTO_2(
        Colour.PINK,
        listOf( // todo fix idk why no work
            Blocks.STONE,
            Blocks.LANTERN,
            Blocks.COARSE_DIRT,
            Blocks.MAGENTA_STAINED_GLASS,
            null,
            Blocks.MAGENTA_STAINED_GLASS,
        ),
        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Ruins Grotto 2", 0, 0, 0, canBeMultiple = true
    ),

    RUINS_GROTTO_3(
        Colour.PINK,
        listOf(
            Blocks.STONE,
            Blocks.DIRT,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS_PANE
        ),
        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Ruins Grotto 3", 0, 0, 0, canBeMultiple = true
    ),

    SHRINE_GROTTO(
        Colour.PINK,
        listOf(
            Blocks.LANTERN,
            Blocks.STONE,
            Blocks.CLAY,
            Blocks.LANTERN,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
        ),
        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Shrine Grotto", 0, 0, 0, canBeMultiple = true
    ),

    SPIRAL_GROTTO(
        Colour.PINK,
        listOf(
            Blocks.POLISHED_ANDESITE,
            Blocks.STONE_BRICKS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS_PANE,
            Blocks.MAGENTA_STAINED_GLASS_PANE,
            Blocks.MAGENTA_STAINED_GLASS_PANE,
            null, null, null, null, null, null,
            Blocks.LANTERN,
        ),
        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Spiral Grotto", 0, 0, 0, canBeMultiple = true
    ),

    WATERFALL_GROTTO(
        Colour.PINK,
        listOf(
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE,
            Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE,
            Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE,
            Blocks.STONE, Blocks.STONE,
            Blocks.WATER
        ),
        StructureType.FAIRY_GROTTO, CrystalHollowsQuarter.ANY, "Waterfall Grotto", 0, 0, 0, canBeMultiple = true
    ),

    GOBLIN_HALL(
        Colour.GREY,
        listOf(
            Blocks.SPRUCE_PLANKS,
            null,
            Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_STAIRS,
            null,
            null,
            Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_STAIRS,
            null,
            null,
            Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_STAIRS,
            null,
            Blocks.SPRUCE_PLANKS
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.GOBLIN_HOLDOUT, "Goblin Hall", 0, 7, 0
    ),

    GOBLIN_RING(
        Colour.GREY,
        listOf(
            Blocks.OAK_FENCE,
            Blocks.SKELETON_SKULL,
            null, null, null, null,
            Blocks.OAK_SLAB,
            Blocks.OAK_SLAB,
            null, null, null,
            Blocks.SPRUCE_PLANKS
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.GOBLIN_HOLDOUT, "Goblin Ring", 0, 11, 0
    ),

    GRUNT_BRIDGE(
        Colour.GREY,
        listOf(
            Blocks.STONE_BRICK_STAIRS,
            null, null, null, null,
            Blocks.STONE_BRICKS,
            Blocks.STONE_BRICKS,
            null,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.STONE_BRICKS,
            null, null, null,
            Blocks.STONE_BRICKS,
            Blocks.SMOOTH_STONE_SLAB
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.MITHRIL_DEPOSITS, "Grunt Bridge", 0, -1, -45
    ),

    CORLEONE_DOCK(
        Colour.BLACK,
        listOf(
            Blocks.POLISHED_GRANITE,
            Blocks.WATER,
            Blocks.WATER,
            null, null, null, null, null, null, null,
            Blocks.STONE,
            Blocks.CYAN_TERRACOTTA,
            Blocks.STONE_BRICKS,
            Blocks.STONE,
            Blocks.FIRE
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.MITHRIL_DEPOSITS, "Corleone Dock", 23, 11, 17
    ),

    CORLEONE_HOLE( // todo fix some day
        Colour.BLACK,
        listOf(
            Blocks.SMOOTH_STONE_SLAB,
            null, null, null, null, null, null, null, null, null, null, null, null, null,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.SMOOTH_STONE_SLAB,
            null,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.STONE_BRICKS
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.MITHRIL_DEPOSITS, "Corleone Hole", 0, -3, 34
    ),

    GRUNT_RAILS_1(
        Colour.GREY,
        listOf(
            Blocks.SPRUCE_PLANKS,
            null,
            Blocks.OAK_WALL_SIGN,
            null, null, null, null,
            Blocks.SPRUCE_PLANKS,
            Blocks.TNT
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.MITHRIL_DEPOSITS, "Grunt Rails 1", 0, 0, 0
    ),

    GRUNT_HERO_STATUE(
        Colour.GREY,
        listOf(
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.COBBLESTONE,
            Blocks.POLISHED_ANDESITE,
            Blocks.COBBLESTONE,
            Blocks.STONE_STAIRS
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.MITHRIL_DEPOSITS, "Grunt Hero Statue", 0, 0, 0
    ),

    SMALL_GRUNT_BRIDGE(
        Colour.GREY,
        listOf(
            Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_STAIRS,
            null,
            Blocks.SPRUCE_STAIRS,
            Blocks.OAK_LOG,
            Blocks.OAK_FENCE,
            Blocks.TORCH
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.MITHRIL_DEPOSITS, "Small Grunt Bridge", 0, 0, 0
    ),

    KEY_GUARDIAN_SPIRAL(
        Colour.GREY,
        listOf(
            Blocks.JUNGLE_STAIRS,
            Blocks.JUNGLE_PLANKS,
            Blocks.GLOWSTONE,
            Blocks.WHITE_CARPET,
            null,
            Blocks.JUNGLE_SLAB,
            null,
            Blocks.JUNGLE_STAIRS,
            Blocks.STONE,
            Blocks.STONE,
            Blocks.STONE
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.JUNGLE, "Key Guardian Spiral", 0, 0, 0
    ),

    SLUDGE_WATERFALLS(
        Colour.GREY,
        listOf(
            Blocks.STONE,
            Blocks.DIRT,
            Blocks.POLISHED_GRANITE,
            Blocks.JUNGLE_STAIRS,
            Blocks.AIR,
            Blocks.AIR,
            Blocks.AIR,
            Blocks.AIR,
            Blocks.AIR
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.JUNGLE, "Sludge Waterfalls", 0, 0, 0
    ),

    SLUDGE_BRIDGES(
        Colour.GREY,
        listOf(
            Blocks.JUNGLE_PLANKS,
            Blocks.JUNGLE_PLANKS,
            Blocks.JUNGLE_PLANKS,
            Blocks.JUNGLE_STAIRS,
            Blocks.JUNGLE_PLANKS,
            Blocks.JUNGLE_PLANKS,
            Blocks.JUNGLE_PLANKS,
            Blocks.JUNGLE_STAIRS,
            Blocks.JUNGLE_PLANKS,
            Blocks.GRANITE,
            Blocks.GRANITE
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.JUNGLE, "Sludge Bridges", 0, 0, 0
    ),

    YOG_BRIDGE(
        Colour.GREY,
        listOf(
            Blocks.STONE_BRICKS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICKS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.ANDESITE,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICKS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.ANDESITE,
            Blocks.STONE_BRICKS,
            Blocks.STONE_BRICKS,
            Blocks.STONE_BRICKS,
            Blocks.RAIL
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.MAGMA_FIELDS, "Yog Bridge", 0, 15, 0
    ),

    ODAWA(
        Colour.GREY,
        listOf(
            Blocks.JUNGLE_LOG,
            Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_STAIRS,
            Blocks.JUNGLE_LOG,
            Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_STAIRS,
            Blocks.JUNGLE_LOG,
            Blocks.JUNGLE_LOG,
            Blocks.JUNGLE_LOG,
            Blocks.HAY_BLOCK,
            Blocks.YELLOW_TERRACOTTA
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.JUNGLE, "Odawa", 0, 0, 0
    ),

    MINI_JUNGLE_TEMPLE(
        Colour.GREY,
        listOf(
            Blocks.POLISHED_ANDESITE,
            Blocks.ANDESITE,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.ANDESITE,
            Blocks.ANDESITE,
            Blocks.STONE,
            Blocks.ANDESITE,
            Blocks.STONE,
            Blocks.ANDESITE,
            Blocks.ANDESITE,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.ANDESITE,
            Blocks.STONE,
            Blocks.ANDESITE
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.JUNGLE, "Mini Jungle Temple", 0, 0, 0
    ),

    PRECURSOR_TRIPWIRE_CHAMBER(
        Colour.GREY,
        listOf(
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.DIORITE,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.SMOOTH_STONE_SLAB
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.PRECURSOR_REMNANTS, "Precursor Tripwire", 0, 0, 0
    ),

    PRECURSOR_TALL_PILLARS(
        Colour.GREY,
        listOf(
            Blocks.POLISHED_DIORITE,
            Blocks.POLISHED_DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.POLISHED_DIORITE,
            Blocks.POLISHED_DIORITE,
            Blocks.POLISHED_ANDESITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.POLISHED_DIORITE,
            Blocks.DIORITE,
            Blocks.POLISHED_ANDESITE,
            Blocks.POLISHED_DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.DIORITE,
            Blocks.POLISHED_DIORITE
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.PRECURSOR_REMNANTS, "Precursor Pillars", 0, 0, 0
    ),

    GOBLIN_HOLE_CAMP(
        Colour.GREY,
        listOf(
            Blocks.NETHERRACK,
            Blocks.NETHERRACK,
            Blocks.OAK_FENCE,
            Blocks.OAK_FENCE,
            Blocks.OAK_LOG,
            Blocks.OAK_LOG
        ),
        StructureType.CH_MOB_SPOTS, CrystalHollowsQuarter.GOBLIN_HOLDOUT, "Goblin Hole Camp", 0, 0, 0
    ),

    GOLDEN_DRAGON(
        Colour.CYAN,
        listOf(
            Blocks.STONE,
            Blocks.RED_TERRACOTTA,
            Blocks.RED_TERRACOTTA,
            Blocks.RED_TERRACOTTA,
            Blocks.SKELETON_SKULL,
            Blocks.RED_WOOL
        ),
        StructureType.GOLDEN_DRAGON, CrystalHollowsQuarter.ANY, "Golden Dragon", 0, -3, 5
    )
}