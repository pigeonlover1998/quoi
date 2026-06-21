package quoi.module.settings

import quoi.module.Module
import quoi.module.settings.impl.TextComponent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class Setting<T> (
    val name: String,
    var description: String = "",
) : ReadWriteProperty<SettingsDSL, T>, PropertyDelegateProvider<SettingsDSL, ReadWriteProperty<SettingsDSL, T>> {

    /**
     * Default value of the setting
     */
    abstract val default: T

    /**
     * Value of the setting
     */
    abstract var value: T

    /**
     * Resets the setting to the default value
     */
    open fun reset() {
        value = default
    }

    var jsonName: String = name
        private set

    override operator fun provideDelegate(thisRef: SettingsDSL, property: KProperty<*>): ReadWriteProperty<SettingsDSL, T> {
        thisRef.register(this)
        return this
    }

    override operator fun getValue(thisRef: SettingsDSL, property: KProperty<*>): T =
        value

    override operator fun setValue(thisRef: SettingsDSL, property: KProperty<*>, value: T) {
        this.value = value
    }

    companion object {

        val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        @Deprecated("use dependsOn")
        fun <K : Setting<T>, T> K.withDependency(dropdown: TextComponent? = null, dependency: () -> Boolean = { true }): K {
//            if (this is UISetting<*>) dropdown?.children?.add(this)
//            parent = dropdown
//            visibilityDependency = { (dropdown?.visibilityDependency?.invoke() ?: true) && dependency() }
            return this
        }

        // allows you to set a custom json name for config file to have duplicate setting names in ui
        fun <K : Setting<T>, T> K.json(name: String): K {
            jsonName = name
            return this
        }
    }
}