package quoi.api.abobaui.constraints

import quoi.api.abobaui.constraints.impl.measurements.Undefined

class Positions(
    x: Constraint.Position,
    y: Constraint.Position
) : Constraints(x, y, Undefined, Undefined)