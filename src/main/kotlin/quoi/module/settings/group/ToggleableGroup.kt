package quoi.module.settings.group

import quoi.module.Module
import quoi.module.settings.impl.SwitchComponent

/**
 * Unlike a [SettingGroup], a [ToggleableGroup] has a state that can be toggled on and off
 * controlled by [SwitchComponent]. It also integrates with the event system so that registered
 * handlers are only active when the state is on. It also calls [onEnable] nad [onDisable] when
 * the state is toggled.
 *
 * ### Example
 * ```kotlin
 *
 * class Test : Module("Test") {
 *     val testGroup = TestGroup(this)
 *
 *     init {
 *         println(testGroup.text)
 *     }
 * }
 *
 * class TestGroup(module: Module) : ToggleableGroup(module, "Test group") { // class makes it reusable
 *     val text by text("text")
 *     val test by switch("test") // saves as "Test group.test" in the config
 *
 *     init {
 *         on<TickEvent.Start> {
 *             println("if you see this the group is enabled")
 *             if (test) println("   this is a test2")
 *         }
 *     }
 * }
 * ```
 *
 * @param module The [Module] that owns this group
 * @param name The switch display name
 * @param default Initial enabled/disabled state of the switch
 * @param desc Description for the switch.
 */
abstract class ToggleableGroup( // todo impl
    module: Module,
    name: String,
    default: Boolean = false,
    desc: String = ""
) : SettingGroup(module, SwitchComponent(name, default, desc)) {

    private val switch = (parent as SwitchComponent).onValueChanged { _, new ->
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