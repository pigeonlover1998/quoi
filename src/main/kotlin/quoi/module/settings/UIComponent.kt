package quoi.module.settings

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.events.AbobaEvent
import quoi.api.abobaui.transforms.impl.Alpha
import quoi.api.animations.Animation
import quoi.api.input.CursorShape
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.impl.render.clickgui.ClickGui.description
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.settingFromK0
import quoi.utils.ui.watch
import kotlin.reflect.KProperty0

abstract class UIComponent<T>(
    name: String,
    desc: String,
) : Setting<T>(name, desc) {

    abstract fun ElementScope<*>.draw(asSub: Boolean = parent != null): ElementScope<*>

    private var visibilityDependency: (() -> Boolean)? = null
    private var isParent = { children.isNotEmpty() }
    private var onValueChanged: (old: T, new: T) -> Unit = { _, _ -> }

    private val isLocallyVisible: Boolean
        get() = (visibilityDependency?.invoke() ?: true) && !hidden

    private val isVisible: Boolean
        get() = isLocallyVisible && (parent?.let { !it.collapsed && it.isVisible } ?: true)

    var parent: UIComponent<*>? = null
    val children: MutableList<UIComponent<*>> = mutableListOf()

    private var hidden = false
    private var collapsed = true
    var forceParent = false
        private set

    val valueUpdated = ValueUpdated()

    /**
     * Hides the component from the [ClickGui]
     */
    open fun hide() = apply {
        hidden = true
    }

    /**
     * Expands the component to show its children
     */
    fun open() = apply {
        collapsed = false
    }

    /**
     * Forces the component to be rendered as parent
     * regardless of whether it's a child or not
     */
    fun asParent() = apply {
        isParent = { true }
        forceParent = true
    }

    /**
     * Callback that's invoked on component value change
     */
    fun onValueChanged(action: (old: T, new: T) -> Unit) = apply {
        this.onValueChanged = action
    }

    fun render(scope: ElementScope<*>, asSub: Boolean = parent != null): ElementScope<*> { // todo recode this mess some day
        var chevronImage: ElementScope<*>? = null

        var gapAnim: Animatable? = null
        var chevronSpaceAnim: Animatable? = null
        var chevronAlphaAnim: Alpha.Animated? = null

        var hasVisible = false
        var showing = false

        val rendering = if (children.isNotEmpty()) {

            hasVisible = children.any { it.isLocallyVisible }
            showing = !collapsed && hasVisible

            chevronSpaceAnim = Animatable(from = 0.px, to = 28.px, swapIf = hasVisible)
            chevronAlphaAnim = Alpha.Animated(to = 1f, from = 0f)
            gapAnim = Animatable(from = 0.px, to = 8.px, swapIf = showing)

            if (hasVisible) chevronAlphaAnim.swap()

            scope.column(size(w = Copying)) {

                group(size(w = Copying - chevronSpaceAnim)) {
                    this.draw(asSub)
                }

                chevronImage = image(
                    image = theme.chevronImage,
                    colour = theme.onSurfaceVariant,
                    constraints = constrain(5.px.alignOpposite, w = 16.px, h = 16.px, y = if (this@UIComponent.value is Boolean) 2.px else 0.px),
                ) {
                    cursor(CursorShape.HAND)

                    val (from, to) = if (collapsed) 180f to 90f else 90f to 180f
                    val rotationAnim = rotation(from = from, to = to)

                    watch(::collapsed) {
                        rotationAnim.animate(0.25.seconds, Animation.Style.EaseInOutQuint)
                        redraw()
                    }

                    transform(chevronAlphaAnim)

                    onClick {
                        collapsed = !collapsed
                        true
                    }
                }

                chevronImage.element.enabled = hasVisible

                divider(gapAnim)

                row(size(w = Copying, h = Bounding)) {
                    block(
                        constrain(w = 2.5.px, h = Copying),
                        colour = theme.primary,
                    )
                    divider(6.px)
                    column(constrain(w = Fill)) {
                        children.forEachIndexed { index, child ->

                            if (index > 0) { // fixes a bug with a trailing gap. I don't like it.
                                val prev = children.take(index)
                                var gapVisible = child.isVisible && prev.any { it.isVisible }

                                val gapAnim = Animatable(
                                    from = 8.px,
                                    to = 0.px,
                                    swapIf = !gapVisible
                                )

                                divider(gapAnim)

                                operation {
                                    val visible = child.isVisible && prev.any { it.isVisible }
                                    if (gapVisible != visible) {
                                        gapVisible = visible
                                        gapAnim.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                                    }
                                    false
                                }
                            }

                            child.render(this, !child.isParent.invoke()).description(child.description)
                        }
                    }
                }
            }
        } else {
            scope.draw(asSub).apply {
                description(description)
            }
        }


        val element = rendering.element
        var hashCode = value.hashCode()
        var oldValue = value

        val alphaAnimation = Alpha.Animated(to = 0f, from = 1f)
        var visible = isVisible

        element.addTransform(alphaAnimation)

        if (element.constraints.height !is Animatable) {
            element.constraints.height = Animatable(
                from = element.constraints.height,
                to = 0.px,
                swapIf = !visible
            )

            if (!visible) {
                alphaAnimation.swap()
            }
        }

        rendering.operation {

            if (visible != isVisible) {
                visible = isVisible
                (element.constraints.height as Animatable).animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                alphaAnimation.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                element.parent?.redraw = true
            }

            if (chevronImage != null && gapAnim != null && chevronSpaceAnim != null && chevronAlphaAnim != null) {
                val hasVisibleCurr = children.any { it.isLocallyVisible }

                if (hasVisibleCurr != hasVisible) {
                    hasVisible = hasVisibleCurr
                    chevronImage.element.enabled = hasVisible
                    chevronSpaceAnim.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                    chevronAlphaAnim.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                }

                val showingCurr = !collapsed && hasVisible
                if (showingCurr != showing) {
                    showing = showingCurr
                    gapAnim.animate(0.2.seconds, Animation.Style.EaseInOutQuint)
                }
            }

            if (hashCode != value.hashCode()) {
                hashCode = value.hashCode()
                onValueChanged.invoke(oldValue, value)
                oldValue = value
                rendering.ui.eventManager.postToAll(valueUpdated, element)
            }
            false
        }
        return rendering
    }

    inline fun ElementScope<*>.onValueChanged(crossinline block: (ValueUpdated) -> Unit) {
        element.registerEvent(valueUpdated) {
            block(it)
            element.redraw()
            false
        }
    }

    class ValueUpdated : AbobaEvent

    companion object {

        private fun UIComponent<*>.addVisibility(condition: () -> Boolean) {
            val prev = visibilityDependency
            visibilityDependency = { (prev?.invoke() ?: true) && condition() }
        }

        /**
         * Set a dependency which determines if the component is visible in the [ClickGui]
         */
        fun <K : UIComponent<T>, T> K.visibleIf(condition: () -> Boolean) = apply {
            addVisibility(condition)
        }

        /**
         * Makes this component a child of the specified component
         *
         * @param condition A visibility condition (defaults to checking if parent's value is true)
         */
        fun <K : UIComponent<T>, T> K.childOf(parent: UIComponent<*>?, condition: () -> Boolean = { (parent?.value as? Boolean) ?: true }) = apply {
            this.parent?.children?.remove(this)
            this.parent = parent
            parent?.children += this
            addVisibility(condition)
        }

        /**
         * Makes this component a child of the component property
         */
        @JvmName("childOfAny")
        fun <K : UIComponent<T>, T> K.childOf(parent: KProperty0<*>?) = apply {
            if (parent == null) return@apply
            val setting = settingFromK0(parent)
            this.parent?.children?.remove(this)
            this.parent = setting
            setting.children += this
        }

        /**
         * Makes this component a child of a boolean property, automatically hiding it when the parent's value is false
         */
        @JvmName("childOfBoolean")
        fun <K : UIComponent<T>, T> K.childOf(parent: KProperty0<Boolean>) = apply {
            childOf(parent as KProperty0<*>)
            addVisibility { parent.get() }
        }

        /**
         * Makes this component a child of the component property
         * showing it only when the condition against the property's value is true.
         *
         * ### Example
         * ```kotlin
         * private val selector by selector(..)
         *
         * private val bool by switch(..).childOf(::selector) { it.index == 0 } // only visible when selected value is 1st
         * ```
         */
        fun <K : UIComponent<T>, T, P> K.childOf(parent: KProperty0<P>?, condition: (P) -> Boolean) = apply {
            childOf(parent as KProperty0<*>)
            addVisibility { condition(parent.get()) }
        }
    }
}