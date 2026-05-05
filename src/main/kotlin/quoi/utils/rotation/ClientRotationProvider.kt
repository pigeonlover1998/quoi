package quoi.utils.rotation

interface ClientRotationProvider {
    fun isClientRotationActive(): Boolean
    fun allowClientKeyInputs(): Boolean = true
}
