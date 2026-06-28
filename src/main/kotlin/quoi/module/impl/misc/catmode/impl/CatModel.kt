package quoi.module.impl.misc.catmode.impl

import net.minecraft.resources.Identifier
import quoi.module.impl.misc.catmode.CatMode
import quoi.module.settings.group.ToggleableGroup

object CatModel : ToggleableGroup(CatMode, "Kitty kitty") { // todo figure some day
    private val catModel by selector("Model", CatModel.Tabby)
    private val self by switch("I'm a cat")
    private val others by switch("He's a cat")
    private val baby by switch("Baby cat")

    @Suppress("unused")
    private enum class CatModel(p: String) {
        AllBlack("all_black"),
        Black("black"),
        BritishShorthair("british_shorthair"),
        Calico("calico"),
        Jellie("jellie"),
        Persian("persian"),
        Ragdoll("ragdoll"),
        Red("red"),
        Siamese("siamese"),
        Tabby("tabby"),
        White("white");

        val path = Identifier.withDefaultNamespace("textures/entity/cat/$p.png")
    }
}