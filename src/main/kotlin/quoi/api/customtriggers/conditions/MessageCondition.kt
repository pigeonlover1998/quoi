package quoi.api.customtriggers.conditions

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeName
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.elements.switch
import quoi.utils.ui.elements.themedInput

@TypeName("message_sent")
class MessageCondition(var pattern: String = "", var isRegex: Boolean = false) : TriggerCondition {

    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Chat) return false
        if (!isRegex) return ctx.message.contains(pattern, ignoreCase = true)

        val regex = Regex(pattern)
        val match = regex.find(ctx.message) ?: return false

        match.groups.forEachIndexed { index, group ->
            if (group != null) ctx.data["%$index%"] = group.value
        }

        Regex("""\(\?<(\w+)>""").findAll(pattern).forEach { res ->
            val name = res.groupValues[1]
            match.groups[name]?.let { ctx.data["%$name%"] = it.value }
        }

        return true
    }

    override fun displayString(): String {
        val msg = if (pattern.length > 25) pattern.take(25) + "..." else pattern
        val regex = if (isRegex) " with regex" else ""
        return "Message \"$msg\"$regex sent"
    }

    override fun ElementScope<*>.draw() = column(size(w = Copying), gap = 10.px) {
        column(size(w = Copying)) {
            text(
                string = "Message",
                size = theme.textSize,
                colour = theme.textSecondary,
            )
            divider(3.px)

            themedInput {
                textInput(
                    string = pattern,
                    pos = at(x = 3.percent),
                    size = theme.textSize,
                    colour = theme.textSecondary,
                    caretColour = theme.caretColour
                ) {
                    maxWidth(Fill - 3.percent)
                    onTextChanged { (string) ->
                        pattern = string
                    }
                }
            }
        }

        row(gap = 7.px) {
            divider(2.px)
            switch(::isRegex, size = 16.px)
            text(
                string = "Regex",
                size = theme.textSize,
                colour = theme.textSecondary,
                pos = at(y = Centre)
            )
        }
    }
}