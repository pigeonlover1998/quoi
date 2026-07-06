package quoi.api.events

import quoi.api.events.core.UnfilteredEvent
import quoi.module.impl.misc.slayers.QuestState

abstract class SlayerEvent {
    class State(val old: QuestState, val new: QuestState): UnfilteredEvent()
}