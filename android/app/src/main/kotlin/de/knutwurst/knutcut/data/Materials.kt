package de.knutwurst.knutcut.data

/** A material preset: a name plus the cut speed and force sent as SP<speed>;FS<force>;. */
data class Material(val id: Int, val name: String, val speed: Int, val force: Int)

object Materials {
    // Conservative starting values (the device's minimum force is 10). These are meant to be tuned
    // on the first real cuts and edited per material; they are not lifted from the stock app, whose
    // material list comes from its server.
    val presets = listOf(
        Material(1, "Vinylfolie", speed = 8, force = 14),
        Material(2, "Aufbügelfolie (HTV)", speed = 8, force = 16),
        Material(3, "Aufkleberpapier", speed = 8, force = 14),
        Material(4, "Papier", speed = 10, force = 12),
        Material(5, "Karton", speed = 6, force = 28),
        Material(6, "Fotokarton", speed = 5, force = 33),
    )

    val default = presets.first()

    const val FORCE_MIN = 10
    const val FORCE_MAX = 160
    const val SPEED_MIN = 1
    const val SPEED_MAX = 30
}
