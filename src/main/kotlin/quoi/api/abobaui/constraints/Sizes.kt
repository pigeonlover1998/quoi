package quoi.api.abobaui.constraints

import quoi.api.abobaui.constraints.impl.measurements.Undefined

class Sizes(
    width: Constraint.Size,
    height: Constraint.Size
) : Constraints(Undefined, Undefined, width, height)