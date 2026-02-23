package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.animations.Animation
import quoi.api.input.CatKeyboard
import quoi.api.input.CatKeyboard.modifierCodes
import quoi.api.input.CatKeys
import quoi.api.input.CatMouse
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.module.settings.Saving
import quoi.module.settings.UISetting
import quoi.utils.ui.cursor
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Objects

class KeybindSetting(
    name: String,
    override val default: Keybinding = Keybinding(CatKeys.KEY_NONE),
    desc: String = ""
) : UISetting<Keybinding>(name, desc), Saving {

    constructor(name: String, defaultKeyCode: Int, desc: String = "") : this(name, Keybinding(defaultKeyCode), desc)

    override var value: Keybinding = default

    private var key: Int
        get() = value.key
        set(newKey) {
            if (newKey == key) return
            value.key = newKey
        }

    fun onPress(block: () -> Unit): KeybindSetting {
        value.onPress = block
        return this
    }

    override fun write(): JsonElement = JsonObject().apply {
        addProperty("key", value.key)
        if (value.modifiers.isNotEmpty()) {
            val mods = JsonArray()
            value.modifiers.forEach { mods.add(it) }
            add("modifiers", mods)
        }
    }

    override fun read(element: JsonElement) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            value.key = obj.get("key").asInt
            value.modifiers.clear()
            obj.get("modifiers")?.asJsonArray?.forEach {
                value.modifiers.add(it.asInt)
            }
        } else if (element.isJsonPrimitive) { // legacy
            value.key = element.asInt
            value.modifiers.clear()
        }
    }

    override fun reset() {
        value = default
    }

    fun getKeyName(): String {
        if (value.key == -1) return "None"
        val sb = StringBuilder()

        value.modifiers.forEach { mod ->
            val name = CatKeyboard.getKeyName(mod) ?: "Err" // should never err
            sb.append("$name + ")
        }

        val mainKey = when (val key = value.key) {
            in 1..Int.MAX_VALUE -> CatKeyboard.getKeyName(key) ?: "Err"
            else -> CatMouse.getButtonName(key + 100)
        }
        sb.append(mainKey.replaceFirstChar { it.uppercaseChar() })

        return sb.toString()
    }

    private val excludes = mutableSetOf<Int>()
    private var includesOnly: MutableSet<Int>? = null

    fun excluding(vararg keys: Int): KeybindSetting {
        excludes.addAll(keys.toList())
        return this
    }

    fun includingOnly(vararg keys: Int): KeybindSetting {
        if (includesOnly == null) includesOnly = mutableSetOf()
        includesOnly!!.addAll(keys.toSet())
        return this
    }

    private fun updateBind(mainKey: Int) {
        value.key = mainKey
        value.modifiers.clear()
        if (mainKey in modifierCodes) return

        modifierCodes.forEach { mod ->
            if (CatKeyboard.isKeyDown(mod)) value.modifiers.add(mod)
        }
    }


    private fun isAllowed(key: Int) = if (includesOnly != null) key in includesOnly!! else key !in excludes

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = row(size(w = Copying)) {
        text(
            string = name,
            size = theme.textSize,
            colour = theme.textSecondary
        )
        block(
            constrain(x = 0.px.alignOpposite, w = Bounding + 5.px, h = Bounding),
            colour = theme.background,
            5.radius()
        ) {
            val thickness = Animatable(from = 2.px, to = 3.px)
            outline(theme.accent, thickness)
            cursor(CursorShape.HAND)

            text(
                string = getKeyName(),
                size = theme.textSize,
                colour = theme.textSecondary
            ) {
                onValueChanged {
                    string = getKeyName()
                }
            }

            onClick(button = 0) {
                ui.focus(element)
            }

            onClick(nonSpecific = true) { (button) ->
                if (ui.eventManager.focused == element) {
                    updateBind(-100 + button)
//                    value.key = -100 + button
                    ui.unfocus()
                    true
                } else false
            }

            onKeyPressed { (key, _) ->
                if (key == CatKeys.KEY_ESCAPE) {
                    value.clear()
                    ui.unfocus()
                } else if (key == CatKeys.KEY_ENTER) {
                    ui.unfocus()
                } else if (isAllowed(key)) {
                    updateBind(key)
                    if (key !in modifierCodes) ui.unfocus()
                } else {
                    ui.unfocus()
                }
                true
            }

            onKeyReleased {
                ui.unfocus()
                true
            }

            onFocusChanged { thickness.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint) }
        }
    }
}

class Keybinding(var key: Int, val modifiers: MutableSet<Int> = mutableSetOf()) {

    /**
     * Intended to active when keybind is pressed.
     */
    var onPress: (() -> Unit)? = null

    /**
     * @return `true` if [key] is held down.
     */
    fun isDown(): Boolean {
        if (key == 0) return false
        val mainKeyDown = if (key < 0) CatMouse.isButtonDown(key + 100) else CatKeyboard.isKeyDown(key)
        if (!mainKeyDown) return false

        return isModifierDown()
    }

    fun isModifierDown() = modifiers.all { CatKeyboard.isKeyDown(it) }

    fun clear() {
        key = -1
        modifiers.clear()
    }

    override fun hashCode(): Int = Objects.hash(key, modifiers)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Keybinding

        if (key != other.key) return false
        if (modifiers != other.modifiers) return false
        if (onPress != other.onPress) return false

        return true
    }
}