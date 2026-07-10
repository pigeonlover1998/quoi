package quoi.module.impl.misc.slayers.blaze

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.events.core.trackedBy
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.Location
import quoi.module.impl.misc.slayers.ISlayer
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.SettingGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.ui.textPair

// todo auto aim.
object BlazeSlayer : SettingGroup(Slayers, "Blaze", area = Island.CrimsonIsle, subarea = "smoldering tomb"), ISlayer {

    override val features = setOf(
        AutoAttune,
        DamageDodge
    )

    private var gummyExpires by slider("gummy_expires", 0L, 0L, Long.MAX_VALUE).hide()
    @Suppress("unused")
    private val gummyBear by textHud("Gummy bear timer") {
        visibleIf { Location.currentArea == Island.CrimsonIsle }
        textPair(
            string = "Gummy bear:",
            supplier = {
                val remaining = gummyExpires - System.currentTimeMillis()
                if (remaining <= 0) "&cInactive" else formatTime(remaining, 0)
            },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.withSettings(::gummyExpires).setting(desc = "Re-heated gummy polar bear timer").asParent()

    init {
        on<ChatEvent.Packet> {
            if (unformatted == "You ate a Re-heated Gummy Polar Bear!")
                gummyExpires = System.currentTimeMillis() + 3_600_000L
        }
    }

    inline val blazeBoss: Blaze?
        get() = Slayers.currentBoss as? Blaze

    val activeDemon: LivingEntity?
        get() {
            val (first, second) = demons ?: return null

            if (first.isActive()) return first
            if (second.isActive()) return second

            return null
        }

    val attune: Attunement?
        get() = (activeDemon ?: blazeBoss)?.getAttune() // since boss is invisible during demon phase we get activeDemon first


    // could fail if someone next to you spawns demons at the same time, but it's very unlikely it'd every happen
    val demons by trackedBy<TickEvent.End, Pair<LivingEntity, LivingEntity>?>(null) { demons ->
        if (Slayers.questState != QuestState.KILLING || blazeBoss?.isInvisible == false) return@trackedBy null // boss becomes invisible during demons phase

        demons?.let { (wither, pig) ->
            if ((!wither.isDeadOrDying && !wither.isRemoved) || (!pig.isDeadOrDying && !pig.isRemoved)) return@trackedBy demons
            return@trackedBy null
        }

        val mobs = getEntities<LivingEntity>(radius = 10.0) {
            it.isPassenger && (it is WitherSkeleton || it is ZombifiedPiglin)
        }

        val wither = mobs.firstOrNull { it is WitherSkeleton } ?: return@trackedBy null
        val pig = mobs.firstOrNull { it is ZombifiedPiglin } ?: return@trackedBy null
        wither to pig
    }

    private fun LivingEntity.isActive() = getAttune() != null

    private fun LivingEntity.getAttune(): Attunement? {
        if (this.isDeadOrDying) return null

        val stand = when {
            this == blazeBoss -> getEntities<ArmorStand>(this.position(), radius = 3.0) { entity -> // id + 2 only works for phase 1. other are random.
                val name = entity.customName?.string?.noControlCodes ?: return@getEntities false
                Attunement.entries.any { name.contains(it.name, true) }
            }.firstOrNull()
            else -> getEntity(this.id + 2) as? ArmorStand
        }

        val name = stand?.customName?.string?.noControlCodes ?: return null
        return Attunement.entries.firstOrNull { name.contains(it.name, true) }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (event is ChatEvent.Packet) {
            return inArea() //&& inSubarea()
        }

        return super.shouldHandle(event)
    }

    override val entitiesForRender: List<Pair<LivingEntity, Colour?>> // untested, maybe works
        get() = demons?.toList()?.map { it to it.getAttune()?.colour }.orEmpty()

    override val debugString: String
        get() = "${activeDemon?.displayName?.string} $attune"

    override val running: Boolean
        get() = super.running && features.any { it.enabled }
}