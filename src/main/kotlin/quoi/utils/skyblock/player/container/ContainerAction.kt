package quoi.utils.skyblock.player.container

interface ContainerAction {
    val block: () -> Unit
    class Click(override val block: () -> Unit) : ContainerAction
    class Other(override val block: () -> Unit) : ContainerAction
}