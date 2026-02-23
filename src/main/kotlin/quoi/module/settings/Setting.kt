package quoi.module.settings

import quoi.module.Module
import quoi.module.settings.impl.DropdownSetting
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class Setting<T> (
    val name: String,
    var description: String = "",
) : ReadWriteProperty<Module, T>, PropertyDelegateProvider<Module, ReadWriteProperty<Module, T>> {

    /**
     * Default value of the setting
     */
    abstract val default: T

    /**
     * Value of the setting
     */
    abstract var value: T

    var hidden = false

    open fun hide(): Setting<T> {
        hidden = true
        return this
    }

    /**
     * Dependency for if it should be shown in the [click gui][Module].
     */
    var visibilityDependency: (() -> Boolean)? = null
        private set

    var parent: DropdownSetting? = null

    /**
     * Resets the setting to the default value
     */
    open fun reset() {
        value = default
    }

    val isVisible: Boolean
        get() = (visibilityDependency?.invoke() ?: true) &&
                !(parent?.collapsed == true && parent?.collapsible == true) &&
                !hidden

    var jsonName: String = name
        private set

    override operator fun provideDelegate(thisRef: Module, property: KProperty<*>): ReadWriteProperty<Module, T> {
        thisRef.register(this)
        return this
    }

    override operator fun getValue(thisRef: Module, property: KProperty<*>): T =
        value

    override operator fun setValue(thisRef: Module, property: KProperty<*>, value: T) {
        this.value = value
    }

    companion object {

        val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun <K : Setting<T>, T> K.withDependency(dropdown: DropdownSetting? = null, dependency: () -> Boolean = { true }): K {
            if (this is UISetting<*>) dropdown?.children?.add(this)
            parent = dropdown
            visibilityDependency = dependency
            return this
        }

        // allows you to set a custom json name for config file to have duplicate setting names in ui
        fun <K : Setting<T>, T> K.json(name: String): K {
            jsonName = name
            return this
        }
    }
}