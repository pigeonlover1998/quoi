package quoi.module.impl.render.clickgui.impl

import quoi.annotations.Internal
import quoi.module.Category
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.MapSetting
import quoi.module.settings.impl.TextComponent
import kotlin.collections.set

@Internal
object Data : SettingGroup(ClickGui, TextComponent("").hide()) {
    val categoryData by MapSetting("category data", mutableMapOf<Category, CategoryData>()).also { setting ->
        Category.entries.forEach {
            setting.value[it] = CategoryData(x = 10f + 265f * it.ordinal, y = 10f, extended = true)
        }
    }

    var currentPet by textInput("Current pet").hide()

    data class CategoryData(var x: Float, var y: Float, var extended: Boolean)
}