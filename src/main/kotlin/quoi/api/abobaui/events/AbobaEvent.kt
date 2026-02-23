package quoi.api.abobaui.events

import quoi.api.input.CatKeyboard.ModState
import quoi.api.input.CatKeyboard.Modifier
import quoi.api.input.CatKeys

interface AbobaEvent {
    interface NonSpecific: AbobaEvent
}

sealed interface Mouse : AbobaEvent {
    data class Clicked(val button: Int) : Mouse {
        data class NonSpecific(val button: Int) : Mouse, AbobaEvent.NonSpecific
    }

    data class Released(val button: Int) : Mouse

    data class Scrolled(val amount: Int) : Mouse, AbobaEvent.NonSpecific

    data object Moved : Mouse

    data object Entered : Mouse

    data object Exited : Mouse
}

sealed interface Lifetime : AbobaEvent.NonSpecific {
    data object Initialised : Lifetime
    data object Uninitialised : Lifetime
}

sealed interface Keyboard : AbobaEvent.NonSpecific {
    data class CharTyped(val key: Char = ' ', val mods: ModState = Modifier) : Keyboard
    data class KeyTyped(val key: Int = CatKeys.KEY_NONE, val mods: ModState = Modifier) : Keyboard
    data class KeyReleased(val key: Int = CatKeys.KEY_NONE, val mods: ModState = Modifier) : Keyboard
}

sealed interface Focus : AbobaEvent.NonSpecific {
    data object Gained : Focus
    data object Lost : Focus
}