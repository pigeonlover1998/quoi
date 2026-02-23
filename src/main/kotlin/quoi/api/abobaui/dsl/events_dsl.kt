@file:OptIn(ExperimentalTypeInference::class)

package quoi.api.abobaui.dsl

import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.events.*
import kotlin.experimental.ExperimentalTypeInference

//--------------//
// Mouse events //
//--------------//

@OverloadResolutionByLambdaReturnType
fun ElementScope<*>.onClick(button: Int = 0, block: (Mouse.Clicked) -> Boolean) {
    element.registerEvent(Mouse.Clicked(button), block)
}

@JvmName("onClickUnit")
@OverloadResolutionByLambdaReturnType
inline fun ElementScope<*>.onClick(button: Int = 0, crossinline block: (Mouse.Clicked) -> Unit) {
    element.registerEventUnit(Mouse.Clicked(button), block)
}

@OverloadResolutionByLambdaReturnType
fun ElementScope<*>.onClick(nonSpecific: Boolean, block: (Mouse.Clicked.NonSpecific) -> Boolean) {
    element.registerEvent(Mouse.Clicked.NonSpecific(0), block)
}

@JvmName("onClickNSUnit")
@OverloadResolutionByLambdaReturnType
inline fun ElementScope<*>.onClick(nonSpecific: Boolean, crossinline block: (Mouse.Clicked.NonSpecific) -> Unit) {
    element.registerEventUnit(Mouse.Clicked.NonSpecific(0), block)
}

inline fun ElementScope<*>.onRelease(button: Int = 0, crossinline block: (Mouse.Released) -> Unit) {
    element.registerEventUnit(Mouse.Released(button), block)
}

@OverloadResolutionByLambdaReturnType
fun ElementScope<*>.onScroll(block: (Mouse.Scrolled) -> Boolean) {
    element.registerEvent(Mouse.Scrolled(0), block)
}

@JvmName("onScrollUnit")
@OverloadResolutionByLambdaReturnType
inline fun ElementScope<*>.onScroll(crossinline block: (Mouse.Scrolled) -> Unit) {
    element.registerEventUnit(Mouse.Scrolled(0), block)
}

@OverloadResolutionByLambdaReturnType
fun ElementScope<*>.onMouseMove(block: (Mouse.Moved) -> Boolean) {
    element.registerEvent(Mouse.Moved, block)
}

@JvmName("_onMouseMove")
@OverloadResolutionByLambdaReturnType
inline fun ElementScope<*>.onMouseMove(crossinline block: (Mouse.Moved) -> Unit) {
    element.registerEventUnit(Mouse.Moved, block)
}

inline fun ElementScope<*>.onMouseEnter(crossinline block: () -> Unit) {
    element.registerEvent(Mouse.Entered) { block(); false }
}

inline fun ElementScope<*>.onMouseExit(crossinline block: () -> Unit) {
    element.registerEvent(Mouse.Exited) { block(); false }
}

inline fun ElementScope<*>.onMouseEnterExit(crossinline block: () -> Unit) {
    element.registerEvent(Mouse.Entered) { block(); false }
    element.registerEvent(Mouse.Exited) { block(); false }
}


//-----------------//
// Keyboard events //
//-----------------//

fun ElementScope<*>.onKeyPressed(block: (Keyboard.KeyTyped) -> Boolean) {
    element.registerEvent(Keyboard.KeyTyped(), block)
}

fun ElementScope<*>.onKeyReleased(block: (Keyboard.KeyReleased) -> Boolean) {
    element.registerEvent(Keyboard.KeyReleased(), block)
}

//-----------------//
// Lifetime events //
//-----------------//

inline fun ElementScope<*>.onAdd(crossinline block: (Lifetime.Initialised) -> Unit) {
    element.registerEventUnit(Lifetime.Initialised, block)
}

inline fun ElementScope<*>.onRemove(crossinline block: (Lifetime.Uninitialised) -> Unit) {
    element.registerEventUnit(Lifetime.Uninitialised, block)
}

//---------------//
// Focus events  //
//---------------//

inline fun ElementScope<*>.onFocus(crossinline block: (Focus.Gained) -> Unit) {
    element.registerEventUnit(Focus.Gained, block)
}

inline fun ElementScope<*>.onFocusLost(crossinline block: (Focus.Lost) -> Unit) {
    element.registerEventUnit(Focus.Lost, block)
}

inline fun ElementScope<*>.onFocusChanged(crossinline block: () -> Unit) {
    element.registerEvent(Focus.Gained) { block(); false }
    element.registerEvent(Focus.Lost) { block(); false }
}

inline fun <E : AbobaEvent> Element.registerEventUnit(event: E, crossinline block: (E) -> Unit) {
    registerEvent(event) {
        block(it)
        false
    }
}