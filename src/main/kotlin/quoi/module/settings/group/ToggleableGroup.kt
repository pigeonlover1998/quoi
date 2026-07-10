package quoi.module.settings.group

import quoi.api.events.core.AreaBoundListener
import quoi.api.events.core.EventListener
import quoi.api.skyblock.location.Area
import quoi.module.Module
import quoi.module.settings.impl.SwitchComponent
import quoi.api.skyblock.location.Location

/**
 * Unlike a [SettingGroup], a [ToggleableGroup] has a state that can be toggled on and off
 * controlled by [SwitchComponent]. It also integrates with the event system so that registered
 * handlers are only active when the state is on. It also calls [onEnable] nad [onDisable] when
 * the state is toggled.
 *
 * ### Example
 * ```kotlin
 *
 * class Test : Module("Test", area = Island.Hub) {
 *     val testGroup = TestGroup(this)
 *
 *     init {
 *         println(testGroup.text)
 *     }
 * }
 *
 * class TestGroup(module: Module) : ToggleableGroup(module, "Test group", subarea = "carnival") { // class makes it reusable
 *     val text by text("text")
 *     val test by switch("test") // saves as "Test group.test" in the config
 *
 *     init {
 *         on<TickEvent.Start> {
 *             println("if you see this the group is enabled, area is Island.Hub and subarea is \"carnival\"")
 *             if (test) println("   this is a test2")
 *         }
 *     }
 * }
 * ```
 * @param parent The [Module] or [SettingGroup] that owns this group
 * @param name The switch display name
 * @param default Initial enabled/disabled state of the switch
 * @param desc Description for the switch.
 * @param area Optional [Area] condition for this group's [EventListener]s to be active.
 * @param subarea Optinal [Location.subarea] string condition for this group's [EventListener]s.
 */
abstract class ToggleableGroup(
    parent: AreaBoundListener,
    name: String,
    default: Boolean = false,
    desc: String = "",
    area: Area? = null,
    subarea: String? = null
) : SettingGroup(parent, SwitchComponent(name, default, desc), area, subarea) {

    private val switch = (component as SwitchComponent).onValueChanged { _, new ->
        if (new) onEnable()
        else onDisable()
    }

    var enabled: Boolean
        get() = switch.value
        set(value) { switch.value = value }

    open fun onEnable() {}

    open fun onDisable() {}

    override val running: Boolean
        get() = super.running && enabled
}