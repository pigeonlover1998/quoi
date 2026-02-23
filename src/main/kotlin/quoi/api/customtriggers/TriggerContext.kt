package quoi.api.customtriggers

interface TriggerContext {
    val data: MutableMap<String, String>

    data object Tick : TriggerContext {
        override val data = mutableMapOf<String, String>()
    }

    class Chat(val message: String, var cancelled: Boolean = false) : TriggerContext {
        override val data = mutableMapOf<String, String>()
    }

    class Sound(val name: String, val volume: Float, val pitch: Float) : TriggerContext {
        override val data = mutableMapOf<String, String>()
    }

    class Key(val key: Int) : TriggerContext {
        override val data = mutableMapOf<String, String>()
    }
}