package quoi.api.customtriggers.actions

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
import quoi.utils.ChatUtils
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.elements.switch
import quoi.utils.ui.elements.themedInput

@TypeName("send_message")
class SendMessageAction(var message: String = "", var client: Boolean = true) : TriggerAction {
    override fun execute(ctx: TriggerContext) {
        var msg = message

        ctx.data.forEach { (key, value) ->
            msg = msg.replace(key, value)
        }

        if (client) ChatUtils.modMessage(msg, prefix = "")
        else ChatUtils.say(msg)
    }

    override fun displayString(): String {
        val msg = if (message.length > 25) message.take(25) + "..." else message
        val side = if (client) "client" else "server"
        return "Send \"$msg\" $side side"
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
                    string = message,
                    pos = at(x = 3.percent),
                    size = theme.textSize,
                    colour = theme.textSecondary,
                    caretColour = theme.caretColour
                ) {
                    maxWidth(Fill - 3.percent)
                    onTextChanged { (string) ->
                        message = string
                    }
                }
            }
        }

        row(gap = 7.px) {
            divider(2.px)
            switch(::client, size = 16.px)
            text(
                string = "Client-side",
                size = theme.textSize,
                colour = theme.textSecondary,
                pos = at(y = Centre)
            )
        }
    }
}