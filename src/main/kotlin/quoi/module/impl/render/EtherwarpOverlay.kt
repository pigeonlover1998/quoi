package quoi.module.impl.render

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.RenderEvent
import quoi.module.Module
import quoi.api.events.TickEvent
import quoi.utils.skyblock.ItemUtils.extraAttributes
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.mixins.accessors.LocalPlayerAccessor
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.BlockTypes
import quoi.utils.rayCast
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

// Kyleen
// https://github.com/Synnerz/devonian/blob/1.21.10/src/main/kotlin/com/github/synnerz/devonian/features/misc/EtherwarpOverlay.kt
object EtherwarpOverlay : Module ("Etherwarp Overlay") {

    private val useCameraHeight by BooleanSetting("Use camera height", desc = "Should be used with Tweaks -> Skyblock only -> Legacy sneak height")
    private val validColour by ColourSetting("Valid colour", Colour.GREEN.withAlpha(60), true)
    private val invalidColour by ColourSetting("Invalid colour", Colour.RED.withAlpha(60), true)
    private val wireframe by BooleanSetting("Show outline")
    private val validLineColour by ColourSetting("Valid colour", Colour.GREEN.withAlpha(60), true).json("Valid outline colour").withDependency { wireframe }
    private val invalidLineColour by ColourSetting("Invalid colour", Colour.RED.withAlpha(60), true).json("Invalid outline colour").withDependency { wireframe }
    private val lineWidth by NumberSetting("Outline width", 2.0, 0.1, 10.0, 0.1).withDependency { wireframe }
    private val depth by BooleanSetting("Depth check")
    private val cancelInteract by BooleanSetting("Cancel interact", desc = "Enables even when looking at an interactable block. (Use with CancelInteract feature)")
    private val tooFar by BooleanSetting("Stop rendering when too far")

    private val validWeapons = mutableListOf("ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "ETHERWARP_CONDUIT")
    private var dist = 0
    var failReason = ""

    //a bit schizo cus we dont ever render failReason but wtv
    init {
        on<TickEvent.Start> {
            dist = 0

            val player = mc.player ?: return@on

            val heldItem = player.getItemInHand(InteractionHand.MAIN_HAND)
            if (
                heldItem.item != Items.DIAMOND_SHOVEL &&
                heldItem.item != Items.DIAMOND_SWORD &&
                heldItem.item != Items.PLAYER_HEAD
            ) return@on

            val itemId = heldItem.skyblockId ?: return@on
            val requireSneak = heldItem.item == Items.DIAMOND_SHOVEL || heldItem.item == Items.DIAMOND_SWORD

            if (requireSneak && !player.isSteppingCarefully) return@on
            if (!validWeapons.contains(itemId)) return@on

            val extraAttributes = heldItem.extraAttributes ?: return@on
            if (requireSneak && !extraAttributes.contains("ethermerge")) return@on

            val tunedTransmission = extraAttributes.get("tuned_transmission")
            val tunedInt = tunedTransmission?.asInt()
            val tuners = if (tunedInt == null || tunedInt.isEmpty) 0 else tunedInt.get()

            dist = 57 + tuners
        }
        on<RenderEvent.World> {
            failReason = ""

            if (dist == 0) return@on

            val player = mc.player ?: return@on
            val world = mc.level ?: return@on

            if (cancelInteract) {
                val target = mc.hitResult
                if (target != null && target.type == HitResult.Type.BLOCK) {
                    val blockTarget = target as BlockHitResult
                    if (BlockTypes.Interactable.contains(world.getBlockState(blockTarget.blockPos).block)) return@on
                }
            }

            val px: Double
            val py: Double
            val pz: Double
            val lookVec: Vec3
            if (useCameraHeight) {
                val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
                val posVec = player.getPosition(pt)
                val camVec = player.getEyePosition(pt)
                px = posVec.x
                py = camVec.y
                pz = posVec.z
                lookVec = player.getViewVector(pt)
            } else {
                val playerAccessor = player as LocalPlayerAccessor
                px = playerAccessor.lastXClient
                py = playerAccessor.lastYClient + if (player.isShiftKeyDown) 1.54f else 1.62f
                pz = playerAccessor.lastZClient
                lookVec = player.calculateViewVector(playerAccessor.lastPitchClient, playerAccessor.lastYawClient)
            }

            var hitResult = rayCast(
                px, py, pz,
                lookVec.x * dist,
                lookVec.y * dist,
                lookVec.z * dist,
                false,
            )

            if (hitResult == null) {
                failReason = "Can't TP: Too far!"
                val maxDist = hypot(256.0, 16.0 * mc.options.effectiveRenderDistance)
                hitResult = rayCast(
                    px,
                    py,
                    pz,
                    lookVec.x * maxDist,
                    lookVec.y * maxDist,
                    lookVec.z * maxDist,
                    false,
                )
                if (hitResult == null) return@on
            } else {
                val bpFoot = hitResult.above(1)
                val bpHead = hitResult.above(2)

                val bsFoot = world.getBlockState(bpFoot)
                val bsHead = world.getBlockState(bpHead)
                if (
                    !BlockTypes.AirLike.contains(bsFoot.block) ||
                    !BlockTypes.AirLike.contains(bsHead.block)
                ) failReason = "Can't TP: No air above!"
            }
            if (tooFar && failReason == "Can't TP: Too far!") return@on
            val colour1 = if (failReason.isEmpty()) validColour else invalidColour

            val box = AABB(hitResult)

            if (colour1.alpha > 0) {
                ctx.drawFilledBox(box, colour1, depth)
            }

            if (wireframe) {
                val colour2 = if (failReason.isEmpty()) validLineColour else invalidLineColour
                ctx.drawWireFrameBox(box, colour2, lineWidth.toFloat(), depth)
            }
        }
    }
}