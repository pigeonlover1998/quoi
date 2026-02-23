package quoi.api.abobaui.elements.impl

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.dsl.registerEventUnit
import quoi.api.abobaui.elements.AbobaDSL
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.events.AbobaEvent
import quoi.api.abobaui.events.Focus
import quoi.api.abobaui.events.Keyboard
import quoi.api.abobaui.events.Mouse
import quoi.api.colour.Colour
import quoi.api.input.CatKeys
import quoi.utils.StringUtils.dropAt
import quoi.utils.StringUtils.removeRangeSafe
import quoi.utils.StringUtils.substringSafe
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer

class TextInput(
    string: String,
    private val placeholder: String,
    font: Font,
    colour: Colour,
    private val placeholderColour: Colour,
    private val caretColour: Colour,
    positions: Positions,
    size: Constraint.Size,
) : Text(string, font, colour, positions, size) {

    override var text: String = string
        set(value) {
            if (field == value) return
            val event = TextChanged(value)
            accept(event)
            if (!event.cancelled) {
                field = event.string
                parent?.redraw()
                previousHeight = 0f
            }
        }

    /**
     * String which gets rendered if [text] is longer than maximum width (if provided).
     */
    private var visibleText: String? = null

    private var caret = text.length
        set(value) {
            if (field == value) return
            field = value.coerceIn(0, text.length)
            caretBlinkTime = System.currentTimeMillis()
        }
    private var selection = text.length

    private var caretX = 0f
    private var selectionWidth = 0f

    /**
     * Offset used to show a section of [text] when it is longer than maximum width (if provided).
     *
     * @see maxWidth
     */
    private var textOffset = 0f

    /**
     * Used to blink the caret every 500ms.
     */
    private var caretBlinkTime = System.currentTimeMillis()

    override fun draw() {
        var offset = 0f
        if (ui.eventManager.focused == this) {

            offset = textOffset
            val time = System.currentTimeMillis()

            val x = x + caretX - offset
            if (selectionWidth != 0f) NVGRenderer.rect(x, y, selectionWidth, height, SELECTION_COLOR) // ctx.rect(x, y, selectionWidth.roundToInt(), height, SELECTION_COLOR)

            if (time - caretBlinkTime < 500) {
//                ctx.drawLine(x, y.toFloat(), x, y.toFloat() + height, Colour.WHITE.rgb, 1f)
                NVGRenderer.line(x, y, x, y + height, 1f, caretColour.rgb)
            } else if (time - caretBlinkTime > 1000) {
                caretBlinkTime = System.currentTimeMillis()
            }
        }

        when {
            text.isEmpty() -> {
                drawText(placeholder, colour = placeholderColour.rgb)
            }
            visibleText != null -> {
                drawText(visibleText!!, colour = this@TextInput.colour!!.rgb)
            }
            else -> {
                drawText(text, x - offset, colour = this@TextInput.colour!!.rgb)
            }
        }
    }

    init {
        scissors = true

        var dragging = false
        var clickCount = 1
        var lastClickTime = 0L

        // Todo: implement after position event of sorts
        // compute visible text (requires renderer to be initialized)
//        registerEventUnit(Lifetime.Initialized) {
//              updateVisibleText()
//        }
        registerEventUnit(Focus.Gained) {
            caretBlinkTime = System.currentTimeMillis()
            visibleText = null
            caretFromMouse()
        }
        registerEventUnit(Focus.Lost) {
            clearSelection()
            textOffset = 0f
            // if text is longer than width (if width is defined),
            // it will cut off the visible part of the text with ".." to indicate it continues
            updateVisibleText()
        }
        registerEventUnit(Mouse.Clicked(0)) {
            if (ui.eventManager.focused != this) {
                ui.focus(this)
            }
            // todo: integrate this into event manager
            val current = System.currentTimeMillis()
            if (current - lastClickTime < 300) clickCount++ else clickCount = 1
            dragging = true
            lastClickTime = current

            when (clickCount) {
                1 -> {
                    caretFromMouse()
                    clearSelection()
                }

                2 -> {
                    selectWord()
                }

                3 -> {
                    selection = 0
                    caret = text.length
                    updateCaretPosition()
                }

                4 -> clickCount = 0
            }
        }
        registerEventUnit(Mouse.Released(0)) {
            dragging = false
        }
        registerEventUnit(Mouse.Moved) {
            if (dragging) {
                caretFromMouse()
            }
        }

        registerEventUnit(Keyboard.CharTyped()) { (char, mods) ->
            if (mods.isCtrlDown && !mods.isShiftDown) {
                when (char) {
                    'v', 'V' -> {
                        val clipboard = ui.clipboard
                        if (clipboard != null) insert(clipboard)
                    }

                    'c', 'C' -> {
                        if (caret != selection) {
                            mc.keyboardHandler.clipboard = text.substringSafe(caret, selection)
                        }
                    }

                    'x', 'X' -> {
                        if (caret != selection) {
                            ui.clipboard = text.substringSafe(caret, selection)
                            deleteSelection()
                        }
                    }

                    'a', 'A' -> {
                        selection = 0
                        caret = text.length
                    }

                    'w', 'W' -> {
                        selectWord()
                    }
                }
            } else {
                insert(char.toString())
            }
            updateCaretPosition()
        }
        registerEventUnit(Keyboard.KeyTyped()) { (key, mods) ->
            when (key) {
                CatKeys.KEY_BACKSPACE -> {
                    if (selection != caret) {
                        deleteSelection()
                    } else if (mods.isCtrlDown) {
                        caret = getPreviousWord()
                        deleteSelection()
                    } else {
                        if (caret != 0) {
                            text = text.dropAt(caret, -1)
                            caret--
                        }
                    }
                    clearSelection()
                }

                CatKeys.KEY_DELETE -> {
                    if (selection != caret) {
                        deleteSelection()
                        clearSelection()
                    } else if (caret != text.length) {
                        text = text.dropAt(caret, 1)
                    }
                }

                CatKeys.KEY_RIGHT -> {
                    if (caret != text.length) {
                        caret = if (mods.isCtrlDown) getNextWord() else caret + 1
                        if (!mods.isShiftDown) selection = caret
                    } else {
                        if (!mods.isShiftDown && selection > 0) selection = caret
                    }
                }

                CatKeys.KEY_LEFT -> {
                    if (caret != 0) {
                        caret = if (mods.isCtrlDown) getPreviousWord() else caret - 1
                        if (!mods.isShiftDown) selection = caret
                    }
                }

                CatKeys.KEY_HOME -> {
                    caret = 0
                    if (!mods.isShiftDown) selection = caret
                }

                CatKeys.KEY_END -> {
                    caret = text.length
                    if (!mods.isShiftDown) selection = caret
                }

                CatKeys.KEY_ESCAPE, CatKeys.KEY_ENTER -> {
                    ui.unfocus()
                }

                else -> {}
            }
            updateCaretPosition()
        }
    }

    override fun prePosition() {
        super.prePosition()
        if (ui.eventManager.focused != this) {
            updateVisibleText()
        }
    }

    override fun getTextWidth(): Float {
        return if (text.isEmpty()) textWidth(placeholder) else super.getTextWidth()
    }

    /**
     * Inserts a provided string into [text].
     *
     * If there is a selection, it will replace it with the string.
     * It will remove any selection active prior.
     */
    private fun insert(string: String) {
        if (caret != selection) {
            text = text.removeRangeSafe(caret, selection)
            caret = if (selection > caret) caret else selection
        }
        // ensure it cant be longer
        val tl = text.length
        text = text.substringSafe(0, caret) + string + text.substring(caret)
        if (text.length != tl) caret += string.length
        clearSelection()
    }

    /**
     * Deletes the selected part of the text.
     */
    private fun deleteSelection() {
        if (caret == selection) return
        text = text.removeRangeSafe(caret, selection)
        caret = if (selection > caret) caret else selection
    }

    /**
     * Updates [caret], based on mouse position on the text input.
     */
    private fun caretFromMouse() {
        val mx = ui.mx - x + textOffset
        var newCaret = 0
        var currWidth = 0f

        for (index in text.indices) {
            val charWidth = textWidth(text[index].toString())
            if ((currWidth + charWidth / 2) > mx) break
            currWidth += charWidth
            newCaret = index + 1
        }
        caret = newCaret.coerceIn(0, text.length)
        updateCaretPosition()
    }

    /**
     * Clears current selection.
     */
    private fun clearSelection() {
        selection = caret
        selectionWidth = 0f
    }

    /**
     * Updates caret's screen position.
     *
     * Will also adjust the visible part of the text, if width is defined.
     */
    private fun updateCaretPosition() {
        if (selection != caret) {
            selectionWidth = textWidth(text.substringSafe(selection, caret))
            if (selection <= caret) selectionWidth *= -1
        } else selectionWidth = 0f

        if (caret != 0) {
            val previousX = caretX
            caretX = textWidth(text.substringSafe(0, caret))

            if (!constraints.width.undefined()) {
                if (previousX < caretX) {
                    if (caretX - textOffset >= width) {
                        textOffset = caretX - width
                    }
                } else {
                    if (caretX - textOffset <= 0f) {
                        textOffset = textWidth(text.substringSafe(0, caret - 1))
                    }
                }
            }
        } else {
            caretX = 0f
            textOffset = 0f
        }
    }

    /**
     * Selects word around the caret.
     */
    private fun selectWord() {
        var start = caret
        var end = caret
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++

        selection = start
        caret = end
        updateCaretPosition()
    }

    private fun getPreviousWord(): Int {
        var start = caret
        if (start == 0) return 0

        // skip
        while (start > 0 && text[start - 1].isWhitespace()) {
            start--
        }

        if (start > 0) {
            // skip symbols
            if (!text[start - 1].isLetterOrDigit() && !text[start - 1].isWhitespace()) {
                while (start > 0 && !text[start - 1].isLetterOrDigit() && !text[start - 1].isWhitespace()) {
                    start--
                }
            }
            // skip chars
            if (start > 0 && text[start - 1].isLetterOrDigit()) {
                while (start > 0 && text[start - 1].isLetterOrDigit()) {
                    start--
                }
            }
        }
        return start
    }

    private fun getNextWord(): Int {
        var end = caret
        if (end >= text.length) return end

        // skip spaces
        while (end < text.length && text[end].isWhitespace()) {
            end++
        }

        if (end < text.length) {
            // skip chars
            if (end < text.length && text[end].isLetterOrDigit()) {
                while (end < text.length && text[end].isLetterOrDigit()) {
                    end++
                }
            }
            // skip symbols
            if (end < text.length && !text[end].isLetterOrDigit() && !text[end].isWhitespace()) {
                while (end < text.length && !text[end].isLetterOrDigit() && !text[end].isWhitespace()) {
                    end++
                }
            }
        }
        return end
    }

    /**
     * Updates [visibleText], which gets used when the [text] is longer than the elements width (only if it defined).
     *
     * Only gets activated if [text] isn't empty and the width is not [Undefined][com.github.stivais.aurora.constraints.impl.measurements.Undefined].
     */
    private fun updateVisibleText() {
        if (text.isNotEmpty() && !constraints.width.undefined()) {
            val maxWidth = width - textWidth("....")
            var isLonger = false
            var visibleLength = 0

            for (index in text.indices) {
                val width = textWidth(text.substring(0, index))
                if (width > maxWidth) {
                    // this doesn't fully prevent awkward placement of the periods (example: "text    ...")
                    // however it should be rare enough that it isn't an issue
                    if (text[index].isWhitespace()) visibleLength--
                    isLonger = true
                    break
                }
                visibleLength++
            }
            visibleText = if (isLonger) text.substring(0, visibleLength) + "..." else null

        } else {
            visibleText = null
        }
    }

    /**
     * Event, which gets registered when the text changes in a [TextInput].
     *
     * The input is able to modify the string.
     * This event is cancellable.
     */
    data class TextChanged(var string: String = "", var cancelled: Boolean = false) : AbobaEvent.NonSpecific {
        fun cancel() {
            cancelled = true
        }
    }

    companion object {

        // this is equivalent to getRGBA(255, 255, 255, 0.5f)
        private const val SELECTION_COLOR: Int = -2147483393

        /**
         * Registers an event listener for [TextChanged].
         *
         * The event gets called when the text is changed inside a [TextInput] element.
         * This event is cancellable.
         */
        @AbobaDSL
        inline fun ElementScope<TextInput>.onTextChanged(crossinline block: (TextChanged) -> Unit) {
            element.registerEvent(TextChanged()) {
                block(it); false
            }
        }

        /**
         * Defines the max width a [TextInput] can be.
         *
         * When the text surpasses that width, it will start scrolling to show the part of the text the caret is over.
         */
        @AbobaDSL
        fun ElementScope<TextInput>.maxWidth(size: Constraint.Size) {
            element.constraints.width = size
        }
    }
}