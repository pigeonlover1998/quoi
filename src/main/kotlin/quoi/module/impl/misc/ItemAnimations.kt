package quoi.module.impl.misc

import quoi.module.Module
import quoi.module.settings.impl.ActionSetting
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.world.effect.MobEffectUtil
import net.minecraft.world.effect.MobEffects
import quoi.api.events.TickEvent
import quoi.utils.skyblock.ItemUtils.lore
import quoi.utils.skyblock.ItemUtils.skyblockId
import kotlin.math.pow

// Kyleen
object ItemAnimations : Module(
    "Item Animations",
    desc = "Change how the held item looks on screen"
) {

    private var x: Double by NumberSetting("X", 0.0, -1.0, 1.0, 0.1)
    private var y: Double by NumberSetting("Y", 0.0, -1.0, 1.0, 0.1)
    private var z: Double by NumberSetting("Z", 0.0, -1.0, 1.0, 0.1)
    private var yaw: Double by NumberSetting("Yaw", 0.0, -180.0, 180.0, 1.0)
    private var pitch: Double by NumberSetting("Pitch", 0.0, -180.0, 180.0, 1.0)
    private var roll: Double by NumberSetting("Roll", 0.0, -180.0, 180.0, 1.0)
    private var scale: Double by NumberSetting("Scale", 0.0, -4.0, 4.0, 0.1)
    private var swingSpeed: Double by NumberSetting("Swing speed", 0.0, -4.0, 4.0, 0.1)
    private val ignoreHand by BooleanSetting("Ignore hand")
    private val ignoreMap by BooleanSetting("Ignore map")
    private val ignoreEffects by BooleanSetting("Ignore effects")
    private val noReequipReset by BooleanSetting("No re-equip reset")
    private val inplaceSwing by BooleanSetting("Swing in place")
    private val noSwing by BooleanSetting("No swing animation")
    private val noSwingTerm by BooleanSetting("No term swing")
    private val noSwingShortbow by BooleanSetting("No shortbow swing")
    private val noHandSway by BooleanSetting("No hand sway")
    private val noEatAnimation by BooleanSetting("No eat animation")
    private val reset by ActionSetting("Reset") { resetSettings() }

    private var swinging = false
    private var swingTimeTick = 0
    private var attackAnim = 0f
    private var prevAttackAnim = 0f

    private fun resetSettings() {
        x = 0.0
        y = 0.0
        z = 0.0
        yaw = 0.0
        pitch = 0.0
        roll = 0.0
        scale = 0.0
        swingSpeed = 0.0
    }

    fun isEnabled(): Boolean = this.enabled
    fun disableReequip(): Boolean = isEnabled() && noReequipReset
    fun disableHandSway(): Boolean = isEnabled() && noHandSway
    fun disableEat(): Boolean = isEnabled() && noEatAnimation

    fun affectHand(): Boolean = !ignoreHand
    fun affectMap(): Boolean = !ignoreMap

    fun disableSwingTranslation(): Boolean {
        return isEnabled() && inplaceSwing
    }

    fun disableSwingRotation(): Boolean {
        if (!isEnabled()) return false
        if (noSwing) return true

        if (!noSwingTerm && !noSwingShortbow) return false

        val held = mc.player?.mainHandItem ?: return false
        if (held.isEmpty) return false

        if (noSwingTerm) {
            if (held.skyblockId == "TERMINATOR") return true
        }

        if (noSwingShortbow) {
            if (held.lore?.any { it.contains("Shortbow", ignoreCase = true) } == true) return true
        }

        return false
    }

    fun disableSwingBob(): Boolean {
        return disableSwingTranslation() || disableSwingRotation()
    }

    private fun getItemScale() = 2.0.pow(scale)
    private fun calcSwingSpeed() = 2.0.pow(swingSpeed)

    fun applyTransformations(pose: PoseStack) {
        if (!isEnabled()) return

        pose.mulPose(Axis.XP.rotationDegrees(pitch.toFloat()))
        pose.mulPose(Axis.YP.rotationDegrees(yaw.toFloat()))
        pose.mulPose(Axis.ZP.rotationDegrees(roll.toFloat()))

        if (x != 0.0 || y != 0.0 || z != 0.0) {
            pose.translate(x.toFloat(), y.toFloat(), z.toFloat())
        }
    }

    fun applyScale(pose: PoseStack) {
        if (!isEnabled()) return
        val s = getItemScale().toFloat()
        if (s != 1f) pose.scale(s, s, s)
    }

    fun getSwingAnimation(pt: Float): Float {
        if (disableSwingRotation()) return 0f

        var d = attackAnim - prevAttackAnim
        if (d < 0.0) d++
        return prevAttackAnim + d * pt
    }

    fun onSwing() {
        if (!isEnabled()) return
        if (swinging && swingTimeTick >= 0 && (swingTimeTick * calcSwingSpeed()) < getCurrentSwingDuration() / 2) return
        swingTimeTick = -1
        swinging = true
    }

    private fun getCurrentSwingDuration(): Int {
        if (ignoreEffects) return 6
        val player = mc.player ?: return 6
        return if (MobEffectUtil.hasDigSpeed(player)) {
            6 - (1 + MobEffectUtil.getDigSpeedAmplification(player))
        } else {
            6 + (1 + (player.getEffect(MobEffects.MINING_FATIGUE)?.amplifier ?: -1)) * 2
        }
    }

    init {
        on<TickEvent.End> {
            if (!enabled || mc.player == null) {
                swinging = false
                return@on
            }

            prevAttackAnim = attackAnim
            val total = getCurrentSwingDuration()

            if (swinging) {
                swingTimeTick++
                val currentProgress = swingTimeTick * calcSwingSpeed()
                if (currentProgress >= total) {
                    swingTimeTick = 0
                    swinging = false
                }
            } else {
                swingTimeTick = 0
            }

            attackAnim = (swingTimeTick * calcSwingSpeed()).toFloat() / total
        }
    }
}