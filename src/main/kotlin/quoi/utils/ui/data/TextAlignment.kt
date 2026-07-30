package quoi.utils.ui.data

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.impl.positions.Alignment
import quoi.api.abobaui.constraints.impl.positions.Centre as CentrePos
import quoi.api.abobaui.dsl.px

enum class TextAlignment(val position: Constraint.Position) {
    Left(0.px),
    Centre(CentrePos),
    Right(Alignment.Opposite(0.px));
}