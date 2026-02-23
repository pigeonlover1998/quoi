package quoi.module.impl.misc

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.*
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.Layout.Companion.divider
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.RefreshableGroup
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.abobaui.elements.impl.layout.Column.Companion.sectionRow
import quoi.api.abobaui.elements.impl.popup
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.abobaui.transforms.impl.Rotation
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.customtriggers.Abobable
import quoi.api.customtriggers.DelayedAction
import quoi.api.customtriggers.Trigger
import quoi.api.customtriggers.TriggerManager
import quoi.api.customtriggers.TriggerManager.actionEntries
import quoi.api.customtriggers.TriggerManager.conditionEntries
import quoi.api.customtriggers.TriggerManager.triggers
import quoi.api.customtriggers.testTrigger
import quoi.api.input.CursorShape
import quoi.config.TypeNamed
import quoi.config.typeName
import quoi.module.Module
import quoi.module.settings.impl.ActionSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.capitaliseFirst
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.elements.numberInput
import quoi.utils.ui.hud.GroupHeight
import quoi.utils.ui.popupX
import quoi.utils.ui.popupY
import quoi.utils.ui.screens.UIScreen.Companion.open
import quoi.utils.ui.elements.selector
import quoi.utils.ui.elements.switch
import quoi.utils.ui.elements.themedInput

object CustomTriggers : Module(
    "Custom Triggers"
) {
    private val addTrigger by ActionSetting("ADD TEST TRIGGER") {
        val trigger = testTrigger()
        TriggerManager.addTrigger("test", trigger)
    }

    private val triggerInfo by ActionSetting("TRIGGER INFO") {
        modMessage(triggers.size)
        triggers.values.flatten().forEach {
            modMessage("""
                ${it.name}
                ${it.actions}
                ${it.conditions}
                -----------------
            """.trimIndent())
        }
    }


    init {
        TriggerManager.init()

        command.sub("ct") {
            open(triggerUi())
        }.description("Opens Custom Triggers editor.")
    }

    private var selectedGroup: String? = null
    private val expandedTriggers = mutableSetOf<String>()

    private lateinit var groupArea: RefreshableGroup
    private lateinit var mainArea: RefreshableGroup

    private var selector: Popup? = null
    private var compSettings: Popup? = null

    /**
     * TODO
     *  comp ui
     *  confirmation on group/trigger/comp remove
     *  ability to rename groups
     *  more comps
     *  icons
     */

    private fun triggerUi() = aboba("Custom Triggers") {
        
//        ui.debug = true
        
        onRemove {
            triggers.save()
        }

        row(
            constrain(
                x = Centre, y = Centre,
                w = AspectRatio(1f).coerceAtLeast(400.px),
                h = 70.percent
            ),
            gap = 15.px
        ) {
            block(
                size(w = 20.percent, h = Copying),
                colour = theme.background,
                10.radius()
            ) {
                dropShadow(
                    colour = Colour.BLACK.withAlpha(0.5f),
                    blur = 5f,
                    spread = 2.5f,
                    radius = 10.radius()
                )
                group(size(Copying - 8.percent, Copying - 2.percent)) {

                    groupArea = refreshableGroup(size(Copying, Fill)) {
                        val scrollable = scrollable(constrain(y = 0.px, w = Copying, h = Copying)) {
                            val constraints = constrain(2.px, 2.px, Copying - 4.px, Bounding + 2.px)
                            column(constraints, gap = 10.px) {
                                val groups = triggers.keys.toMutableList()
                                if ("General" in groups) {
                                    groups.remove("General")
                                    groups.add(0, "General")
                                }

                                groups.forEach { group ->
                                    block(
                                        size(Fill, 50.px),
                                        colour = theme.panel,
                                        10.radius()
                                    ) {
                                        outline(theme.border, thickness = 2.px)
                                        cursor(CursorShape.HAND)
                                        hoverEffect(1.1f)
                                        text(
                                            string = group,
                                            colour = theme.textPrimary
                                        )

                                        onClick {
                                            selectedGroup = group
                                            mainArea.refresh()
                                            true
                                        }

                                        if (group != "General") onClick(button = 1) {
                                            triggers.remove(group)
                                            triggers.save()
                                            mainArea.refresh()
                                            groupArea.refresh()
                                            true
                                        }
                                    }
                                }


                            }
                        }

                        onScroll { (amount) ->
                            scrollable.scroll(amount * -75f)
                        }
                    }

                    block(
                        constrain(y = 0.px.alignOpposite, w = Copying, h = 50.px),
                        colour = theme.background,
                        10.radius()
                    ) {
                        outline(theme.border, thickness = 2.px)
                        cursor(CursorShape.HAND)
                        hoverEffect(1.1f)
                        row {
                            block( // todo replace with icon
                                size(20.px, 20.px),
                                colour = Colour.BLACK
                            )
                            text(
                                string = "Add",
                                colour = theme.textSecondary,
                                size = 20.px
                            )
                        }

                        onClick {
                            val new = "Group ${triggers.values.size + 1}"
                            triggers[new] = mutableListOf()
                            triggers.save()
                            selectedGroup = new
                            mainArea.refresh()
                            groupArea.refresh()
                            true
                        }
                    }
                }.element.moveToTop()
            }

            block(
                size(w = Fill, h = Copying),
                colour = theme.background,
                10.radius()
            )  {
//                outline(theme.border, thickness = 2.px)
                dropShadow(
                    colour = Colour.BLACK.withAlpha(0.5f),
                    blur = 5f,
                    spread = 2.5f,
                    radius = 10.radius()
                )

                mainArea = refreshableGroup(size(Copying - 4.percent, Copying - 3.percent)) {

                    val currentTriggers = triggers[selectedGroup] ?: run {
                        text(
                            string = "Select a group to edit",
                            colour = theme.textSecondary,
                            pos = at(x = Centre, y = Centre),
                            size = 5.percent
                        )
                        return@refreshableGroup
                    }

                    val group = selectedGroup!!

                    val scrollable = scrollable(constrain(y = 0.px, w = Copying, h = Copying)) {
                        val constraints = constrain(2.px, 2.px, Copying - 4.px, Bounding + 2.px)
                        column(constraints, gap = 10.px) {
                            currentTriggers.forEach { trigger ->
                                drawTrigger(group, trigger)
                            }

                            block(
                                size(w = Copying, h = 45.px),
                                colour = theme.background,
                                5.radius()
                            ) {
                                outline(theme.border, thickness = 2.px)
                                cursor(CursorShape.HAND)
                                hoverEffect(1.1f)
                                row(gap = 5.px) {
                                    block( // todo replace with icon
                                        size(20.px, 20.px),
                                        colour = Colour.BLACK
                                    )
                                    text(
                                        string = "Add trigger",
                                        colour = theme.textSecondary,
                                        size = 20.px
                                    )
                                }

                                onClick {
                                    TriggerManager.addTrigger(group, Trigger("Trigger ${triggers[group]?.size?.plus(1)}"))
                                    mainArea.refresh()
                                    true
                                }
                            }
                        }
                    }

                    onScroll { (amount) ->
                        scrollable.scroll(amount * -75f)
                    }
                }
            }
        }
    }

    private fun ElementScope<*>.drawTrigger(group: String, trigger: Trigger) = block(size(Copying, GroupHeight), theme.panel, 5.radius()) {
        fun isExpanded() = trigger.id in expandedTriggers

        outline(theme.border, 2.px)

        val height = Animatable(from = 0.px, to = Bounding, swapIf = isExpanded())
        lateinit var rotation: Rotation.Animated

        val inputBg = Colour.Animated(
            from = theme.panel,
            to = theme.background,
            swapIf = isExpanded()
        )

        val inputOl = Colour.Animated(
            from = theme.panel,
            to = theme.border,
            swapIf = isExpanded()
        )

        column(size(w = Copying)) {

            sectionRow(45.px, gap = 4.px) {
                divider(10.px)

                image(
                    image = theme.chevronImage,
                    constrain(y = Centre, w = 24.px, h = 24.px)
                ) {
                    val (from, to) = if (!isExpanded()) 0f to 90f else 90f to 0f
                    rotation = rotation(from = from, to = to)
                }

                block(
                    constrain(y = Centre, w = 30.percent, h = 65.percent),
                    colour = inputBg,
                    5.radius()
                ) {
                    outline(inputOl, thickness = 2.px)
                    textInput( // todo fix it crashing sometimes
                        string = trigger.name,
                        colour = theme.textPrimary,
                        caretColour = theme.caretColour,
                        size = Copying - 40.percent,
                        pos = at(x = 3.percent)
                    ) {
                        maxWidth(Copying - 5.percent)

                        var new = trigger.name

                        onClick {
                            return@onClick isExpanded()
                        }

                        onTextChanged { (text) ->
                            new = if (text.length > 20) text.take(20) else text
                        }

                        onFocusLost {
                            if (new == trigger.name) return@onFocusLost
                            if (new.isEmpty()) {
                                string = trigger.name
                                return@onFocusLost
                            }
                            trigger.name = new
                            expandedTriggers.remove(trigger.name)
                            expandedTriggers.add(new)
                            triggers.save()
                        }
                        cursor(CursorShape.IBEAM)
                    }
                }

                row(at(x = 2.percent.alignOpposite, y = Centre), gap = 7.5.px) {
                    switch(
                        trigger::enabled,
                        size = 20.px,
                        colour = theme.border,
                        pos = at(y = Centre)
                    )

                    text(
                        string = "×",
                        colour = theme.textSecondary,
                        size = 22.5.px,
                    ) {
//                    hoverEffect(1.1f)
//                    var popup: Popup? = null
                        onClick {
//                        popup = popup(copies(), smooth = true) {
//                            block(
//
//                            )
//                        }

                            triggers[group]?.remove(trigger)
                            expandedTriggers.remove(trigger.id)
                            triggers.save()
                            mainArea.refresh()

                            true
                        }
                    }
                }

                onClick {
                    if (isExpanded()) expandedTriggers.remove(trigger.id) else expandedTriggers.add(trigger.id)

                    rotation.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
                    height.animate(0/*.25*/.seconds, style = Animation.Style.EaseInOutQuint)
                    inputBg.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
                    inputOl.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)

                    this@block.redraw()
                    true
                }

                cursor(CursorShape.HAND)
            }



            column(constrain(x = 3.percent, w = Copying - 6.percent, h = height), gap = 20.px) {
                divider(15.px)

                row(gap = 5.px) {
                    text(
                        string = "CONDITIONS",
                        colour = theme.textPrimary,
                        size = 18.px
                    )

                    block(
                        constrain(y = Centre, w = Bounding + 2.px, h = Bounding + 2.px),
                        colour = theme.background,
                        4.radius()
                    ) {
                        text(
                            string = "(All must match - AND logic)",
                            colour = theme.textSecondary,
                            size = 12.px
                        )
                    }
                }

                compList(
                    comps = trigger.conditions,
                    displayString = { it.displayString() },
                    entries = conditionEntries,
                    addLabel = "Add condition",
                    onOpenSettings = { condition ->
                        compSettings = componentSettings("${condition.name} Condition", condition)
                    }
                )

                text(
                    string = "ACTIONS",
                    colour = theme.textPrimary,
                    size = 18.px
                )

                compList(
                    comps = trigger.actions,
                    displayString = { it.action.displayString() },
                    entries = actionEntries.map { (name, entry) -> name to { DelayedAction(entry()) } },
                    addLabel = "Add action",
                    onOpenSettings = { delayedAction ->
                        compSettings = componentSettings(
                            title = "${delayedAction.action.name} Action",
                            comp = delayedAction.action,
                            extra = {
                                row(size(Copying, Bounding)) {
                                    text(
                                        string = "Delay: ",
                                        colour = theme.textSecondary,
                                        size = theme.textSize,
                                        pos = at(y = Centre)
                                    )
                                    themedInput(size = size(w = Bounding + 5.px, h = 18.px)) {
                                        numberInput(
                                            delayedAction::delay,
                                            min = 0,
                                            max = 200,
                                            colour = theme.textSecondary,
                                            size = theme.textSize
                                        )
                                    }
                                }
                            }
                        )
                    }
                )
                divider(0.px)
            }
        }
    }

    private val TypeNamed.name get() = typeName.split("_", " ").joinToString(" ") { it.capitaliseFirst() }

    private fun <T> ElementScope<*>.compList(
        comps: MutableList<T>,
        displayString: (T) -> String,
        entries: List<Pair<String, () -> T>>,
        addLabel: String,
        onOpenSettings: (T) -> Unit
    ) = refreshableGroup(size(Copying, Bounding)) {
        column(size(w = Copying), gap = 15.px) {
            comps.forEach { comp ->
                block(
                    size(Copying, 35.px),
                    colour = theme.panel,
                    5.radius()
                ) {
                    outline(theme.border, thickness = 2.px)
                    hoverEffect(1.1f)
                    cursor(CursorShape.HAND)

                    row(constrain(x = 5.px, w = Copying)) {
                        textSupplied(
                            supplier = { displayString(comp) },
                            colour = theme.textPrimary,
                            size = 20.px
                        )

                        text(
                            string = "×",
                            colour = theme.textSecondary,
                            size = 22.5.px,
                            pos = at(x = 2.percent.alignOpposite, y = Centre)
                        ) {
                            onClick {
                                comps.remove(comp)
                                triggers.save()
                                this@refreshableGroup.element.refresh()
                                true
                            }
                        }
                    }

                    onClick {
                        compSettings?.closePopup()
                        onOpenSettings(comp)
                        true
                    }
                }
            }

            outlineBlock(
                size(w = Copying, h = 40.px),
                colour = theme.border,
                thickness = 2.px,
                5.radius()
            ) {
                row(at(x = 5.px), gap = 5.px) {
                    block( // todo replace with icon
                        size(20.px, 20.px),
                        colour = Colour.BLACK
                    ) {
                        cursor(CursorShape.HAND)
                        onClick {
                            selector?.closePopup()
                            selector = selector(
                                entries = entries,
                                displayString = { it.first },
                                pos = at(popupX(-70f), popupY(corner = true)),
                                size = size(120.px, 30.px)
                            ) { (_, new) ->
                                comps.add(new())
                                triggers.save()
                                this@refreshableGroup.element.refresh()
                            }
                            true
                        }
                    }
                    text(
                        string = addLabel,
                        colour = theme.textSecondary,
                        size = 15.px,
                        pos = at(y = Centre)
                    )
                }
            }
        }
    }

    private fun ElementScope<*>.componentSettings(
        title: String,
        comp: Abobable,
        extra: (ElementScope<*>.() -> Unit)? = null
    ) = popup(copies(), smooth = false) {

        onRemove {
            triggers.save()
        }

        onClick {
            closePopup()
            compSettings = null
        }

        block(copies(), colour = Colour.BLACK.withAlpha(0.5f))

        block(
            size(400.px, Bounding),
            theme.panel,
            7.radius()
        ) {
            outline(theme.border, 2.px)
            onClick {
                true
            }

            column(constrain(x = 10.px, y = 10.px, w = Copying - 20.px, h = Bounding), gap = 10.px) {
                sectionRow(25.px) {
                    text(
                        string = title,
                        colour = theme.textPrimary,
                        size = 100.percent,
                        pos = at(y = Centre)
                    )

                    text(
                        string = "×",
                        colour = theme.textSecondary,
                        size = 22.5.px,
                        pos = at(x = 2.percent.alignOpposite, y = Centre)
                    ) {
                        cursor(CursorShape.HAND)
                        onClick {
                            closePopup()
                            compSettings = null
                            true
                        }
                    }
                }

                extra?.invoke(this)

                comp.apply {
                    draw()
                }

                divider(5.px)

            }

        }

    }
}