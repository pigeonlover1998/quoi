@file:Suppress("nothing_to_inline")
package quoi.api.abobaui.elements

import quoi.api.abobaui.AbobaUI
import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Constraints
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.copies
import quoi.api.abobaui.dsl.percent
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.impl.*
import quoi.api.abobaui.elements.impl.layout.Column
import quoi.api.abobaui.elements.impl.layout.Grid
import quoi.api.abobaui.elements.impl.layout.Row
import quoi.api.abobaui.events.AbobaEvent
import quoi.api.abobaui.operations.Operation
import quoi.api.abobaui.transforms.Transform
import quoi.api.colour.Colour
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.data.Gradient
import quoi.utils.ui.rendering.Image
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.data.Radii

open class ElementScope<E : Element>(val element: E) {

    inline val ui: AbobaUI
        get() = element.ui

    inline var enabled: Boolean
        get() = element.enabled
        set(value) {
            element.enabled = value
        }

    inline fun redraw() {
        val element = element.parent ?: element
        element.redraw()
    }

    @AbobaDSL
    inline fun block(
        constraints: Constraints,
        colour: Colour,
        radius: Radii? = null,
        block: ElementScope<Block>.() -> Unit = {}
    ) = Block(constraints, colour, radius).scope(block)

    @AbobaDSL
    inline fun block(
        constraints: Constraints,
        colours: Pair<Colour, Colour>,
        gradient: Gradient,
        radius: Radii? = null,
        block: ElementScope<Block.Gradient>.() -> Unit = {}
    ) = Block.Gradient(constraints, colours.first, colours.second, gradient, radius).scope(block)

    @AbobaDSL
    inline fun ctxBlock(
        constraints: Constraints,
        colour: Colour,
        block: ElementScope<Block>.() -> Unit = {}
    ) = Block.CtxBlock(constraints, colour).scope(block)

    @AbobaDSL
    inline fun text(
        string: String,
        font: Font = NVGRenderer.defaultFont,
        colour: Colour = Colour.WHITE,
        pos: Positions = at(Undefined, Undefined),
        size: Constraint.Size = 50.percent,
        block: ElementScope<Text>.() -> Unit = {}
    ) = Text(string, font, colour, pos, size).scope(block)

    @AbobaDSL
    inline fun column(
        constraints: Constraints = size(Bounding, Bounding),
        gap: Constraint.Size? = null,
        block: ElementScope<Column>.() -> Unit = {}
    ) = Column(constraints, gap).scope(block)

    @AbobaDSL
    inline fun row(
        constraints: Constraints = size(Bounding, Bounding),
        gap: Constraint.Size? = null,
        block: ElementScope<Row>.() -> Unit = {}
    ) = Row(constraints, gap).scope(block)

    @AbobaDSL
    inline fun grid(
        constraints: Constraints = size(Bounding, Bounding),
        padding: Constraint.Size? = null,
        block: ElementScope<Grid>.() -> Unit = {}
    ) = Grid(constraints, padding).scope(block)

    @AbobaDSL
    inline fun group(
        constraints: Constraints = size(Bounding, Bounding),
        block: ElementScope<Group>.() -> Unit = {}
    ) = Group(constraints).scope(block)

    @AbobaDSL
    inline fun scrollable(
        constraints: Constraints = size(Bounding, Bounding),
        block: ElementScope<Scrollable>.() -> Unit
    ) = Scrollable(constraints).scope(block)

    @AbobaDSL
    inline fun textInput(
        string: String = "",
        placeholder: String = "",
        font: Font = NVGRenderer.defaultFont,
        colour: Colour = Colour.WHITE,
        placeHolderColour: Colour = Colour.WHITE,
        caretColour: Colour = Colour.WHITE,
        pos: Positions = at(Undefined, Undefined),
        size: Constraint.Size = 50.percent,
        block: ElementScope<TextInput>.() -> Unit
    ) = TextInput(string, placeholder, font, colour, placeHolderColour, caretColour, pos, size).scope(block)

    @AbobaDSL
    inline fun dropShadow(
        constraints: Constraints = copies(),
        colour: Colour = Colour.BLACK,
        blur: Float,
        spread: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        radius: Radii? = null,
    ) {
        val shadow = Shadow(constraints, colour, blur, spread, offsetX, offsetY, radius)
        element.addShadow(shadow)
        element.init()
    }

    @AbobaDSL
    inline fun image(
        image: Image,
        constraints: Constraints,
        radius: Radii? = null,
        block: ElementScope<ImageElement>.() -> Unit = {}
    ) = ImageElement(image, constraints, radius).scope(block)

    @AbobaDSL
    inline fun image(
        textureId: Int,
        constraints: Constraints,
        radius: Radii? = null,
        block: ElementScope<GlImageElement>.() -> Unit = {}
    ) = GlImageElement(textureId, constraints, radius).scope(block)

    inline fun <E : Element> E.scope(block: ElementScope<E>.() -> Unit) = createScope(this, block)

    inline fun <E : Element> createScope(element: E, block: ElementScope<E>.() -> Unit): ElementScope<E> {
        this.element.addElement(element)
        val scope = ElementScope(element)
        scope.block()
        element.init()
        return scope
    }

    inline fun onEvent(event: AbobaEvent, crossinline block: () -> Boolean) {
        element.registerEvent(event) {
            block()
        }
    }

    inline fun <E : AbobaEvent> onEvent(event: E, crossinline block: (E) -> Boolean) {
        element.registerEvent(event) { e ->
            block(e)
        }
    }


    inline fun <E : Element> E.add() {
        element.addElement(this)
        element.init()
    }

    fun operation(operation: Operation) = element.ui.addOperation(operation)

    fun transform(transform: Transform) = element.addTransform(transform)
}

@DslMarker
annotation class AbobaDSL