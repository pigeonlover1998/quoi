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
import quoi.utils.skyblock.ItemUtils.loreString
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.ui.settingFromK0
import kotlin.math.pow

// Kyleen
object ItemAnimations : Module(
    "Item Animations",
    desc = "Changes how the held item looks on screen"
) {

    private var x by NumberSetting("X", 0.0f, -1.0f, 1.0f, 0.1)
    private var y by NumberSetting("Y", 0.0f, -1.0f, 1.0f, 0.1)
    private var z by NumberSetting("Z", 0.0f, -1.0f, 1.0f, 0.1)
    private var yaw by NumberSetting("Yaw", 0.0f, -180.0f, 180.0f, 1.0)
    private var pitch by NumberSetting("Pitch", 0.0f, -180.0f, 180.0f, 1.0)
    private var roll by NumberSetting("Roll", 0.0f, -180.0f, 180.0f, 1.0)
    private var scale by NumberSetting("Scale", 0.0, -4.0, 4.0, 0.1)
    private var swingSpeed by NumberSetting("Swing speed", 0.0, -4.0, 4.0, 0.1)
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

    override fun onDisable() {
        super.onDisable()
        swinging = false
    }

    private fun resetSettings() {
        setOf(
            ::x, ::y, ::z,
            ::yaw, ::pitch,
            ::roll,::scale, ::swingSpeed
        ).forEach { settingFromK0(it).reset() }
    }

    private fun calcSwingSpeed() = 2.0.pow(swingSpeed)

    private fun disableSwingRotation(): Boolean {
        if (!enabled) return false
        if (noSwing) return true

        if (!noSwingTerm && !noSwingShortbow) return false

        val held = mc.player?.mainHandItem ?: return false
        if (held.isEmpty) return false

        if (noSwingTerm) {
            if (held.skyblockId == "TERMINATOR") return true
        }

        if (noSwingShortbow) {
            if (held.loreString?.contains("Shortbow", ignoreCase = true) == true) return true
        }

        return false
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

    @JvmStatic fun disableReequip(): Boolean = enabled && noReequipReset
    @JvmStatic fun disableHandSway(): Boolean = enabled && noHandSway
    @JvmStatic fun disableEat(): Boolean = enabled && noEatAnimation

    @JvmStatic fun affectHand(): Boolean = !ignoreHand
    @JvmStatic fun affectMap(): Boolean = !ignoreMap

    @JvmStatic
    fun disableSwingTranslation(): Boolean {
        return enabled && inplaceSwing
    }

    @JvmStatic
    fun disableSwingBob(): Boolean {
        return disableSwingTranslation() || disableSwingRotation()
    }

    @JvmStatic
    fun applyTransformations(pose: PoseStack) {
        if (!enabled) return

        pose.mulPose(Axis.XP.rotationDegrees(pitch))
        pose.mulPose(Axis.YP.rotationDegrees(yaw))
        pose.mulPose(Axis.ZP.rotationDegrees(roll))

        if (x != 0.0f || y != 0.0f || z != 0.0f) {
            pose.translate(x, y, z)
        }
    }

    @JvmStatic
    fun applyScale(pose: PoseStack) {
        if (!enabled) return
        val s = 2.0.pow(scale).toFloat()
        if (s != 1f) pose.scale(s, s, s)
    }

    @JvmStatic
    fun getSwingAnimation(pt: Float): Float {
        if (disableSwingRotation()) return 0f

        var d = attackAnim - prevAttackAnim
        if (d < 0.0) d++
        return prevAttackAnim + d * pt
    }

    @JvmStatic
    fun onSwing() {
        if (!enabled) return
        if (swinging && swingTimeTick >= 0 && (swingTimeTick * calcSwingSpeed()) < getCurrentSwingDuration() / 2) return
        swingTimeTick = -1
        swinging = true
    }

    init {
        on<TickEvent.End> {
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