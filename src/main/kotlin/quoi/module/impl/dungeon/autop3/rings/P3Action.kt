package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Input
import quoi.api.colour.Colour
import quoi.api.input.MutableInput
import quoi.config.TypeNamed

sealed interface P3Action : TypeNamed {
    suspend fun execute(player: LocalPlayer)
    val colour: Colour
    val priority: Int
    fun feedbackMessage(): String
    fun execute(): Boolean = true
    fun tick(player: LocalPlayer, clientInput: Input, input: MutableInput): Boolean = true
    fun isStop(): Boolean = false
    fun shouldStop(): Boolean = false
}
