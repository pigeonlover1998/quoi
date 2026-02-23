package quoi.utils.ui.hud

import quoi.module.Module

abstract class ScopedHud<S : Hud.Scope>(
    name: String,
    module: Module,
    toggleable: Boolean,
    private val content: S.() -> Unit
) : Hud(name, module, toggleable) {

    override val builder: Scope.() -> Unit
        get() = { content(createScope(this)) }

    abstract fun createScope(base: Scope): S
}