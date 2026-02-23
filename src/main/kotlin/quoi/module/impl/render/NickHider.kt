package quoi.module.impl.render

import quoi.api.colour.Colour
import quoi.module.Module
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.StringSetting
import quoi.utils.ChatUtils.literal
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence
import kotlin.math.min

// https://github.com/Ownwn/Client-Custom-Name
object NickHider : Module(
    "Nick Hider",
    desc = "Visually hides player name."
) { // todo colour setting
    val customName by StringSetting("Name", "cat", desc = "Player name to display.")
    private val nameColour by ColourSetting("Name colour", Colour.ORANGE, desc = "Colour of the displayed name.")

    private fun getUsername() = mc.player?.name?.string?.takeIf { enabled && customName != it }

    @JvmStatic
    fun replaceName(string: String): String {
        val u = getUsername() ?: return string
        var out = string
        repeat(10) { if (out.contains(u)) out = out.replace(u, customName) else return out }
        return out
    }

    @JvmStatic
    fun replaceName(text: FormattedCharSequence): FormattedCharSequence {
        val username = getUsername() ?: return text

        val originalTextArray = openOrderedText(text)
        if (!originalTextArray.joinToString("") { it.string }.contains(username)) return text

        var currentText = text

        repeat(10) {
            if (currentText.contains(username))
                currentText = replaceOrderedText(
                    currentText,
                    username,
                    literal(customName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColour.rgb)))
                )
            else return currentText
        }

        return currentText
    }

    private fun openOrderedText(text: FormattedCharSequence): MutableList<Component> {
        val textArray: MutableList<Component> = mutableListOf()

        text.accept { _, style, codePoint ->
            textArray += Component.literal(codePoint.toChar().toString()).setStyle(style)
            true
        }
        return textArray
    }

    fun replaceOrderedText(original: FormattedCharSequence, target: String, replacement: Component): FormattedCharSequence =
        replaceOrderedText(original, openOrderedText(original), target, replacement)

    private fun replaceOrderedText( // cba figuring what it does
        originalText: FormattedCharSequence,
        originalTextArray: MutableList<Component>, // list of single characters
        target: String,
        replacement: Component
    ): FormattedCharSequence {
        // include originalText in case of early return, or if no changes are made

        val targetIndex = originalTextArray.joinToString("") { it.string }.indexOf(target)
        if (targetIndex == -1) return originalText // text does not contain target


        val replacementString = replacement.string
        val targetLength = target.length

        val replacementStyleHasSiblings = replacement.siblings.size >= replacementString.length


        var replacementStrIndex = 0 // index in the replacement string
        var appendedExtraReplacement = false
        val newText = Component.empty()
        var shortOffset = targetLength - replacementString.length


        for ((i, currentCharacter) in originalTextArray.withIndex()) {
            var currentStyle = currentCharacter.style
            var currentStr = currentCharacter.string

            // target.len > replacement.len so we need to remove some characters
            if (replacementStrIndex >= replacementString.length && shortOffset > 0) {
                shortOffset--
                currentStr = ""
            }

            // whether to replace character
            if (i > (targetIndex - 1) && replacementStrIndex < min(targetLength, replacementString.length)) {
                currentStr = replacementString[replacementStrIndex].toString()

                currentStyle = if (replacementStyleHasSiblings) replacement.siblings[replacementStrIndex].style
                else replacement.style
                replacementStrIndex++

            }

            newText.append(Component.literal(currentStr).setStyle(currentStyle))

            // replacement.len > target.len so we need to add extra chars to the text
            if (!appendedExtraReplacement && replacementString.length > targetLength && replacementStrIndex == targetLength) {

                if (!replacementStyleHasSiblings) {
                    newText.append(Component.literal(replacementString.substring(targetLength)).setStyle(currentStyle))
                } else {
                    // account for styles of individual chars in replacement
                    for ((replacementIterator, theChar) in replacementString.substring(targetLength).toCharArray()
                        .withIndex()) {
                        newText.append(
                            Component.literal(theChar.toString())
                                .setStyle(replacement.siblings[replacementIterator + replacementStrIndex].style)
                        )
                    }
                }

                appendedExtraReplacement = true
            }
        }

        return if (newText.string.isEmpty()) originalText else newText.visualOrderText
    }

    private fun FormattedCharSequence.contains(string: String): Boolean =
        openOrderedText(this).joinToString("") { it.string }.contains(string)
}