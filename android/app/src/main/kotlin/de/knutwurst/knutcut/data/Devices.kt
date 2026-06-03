package de.knutwurst.knutcut.data

/** A plotter model and its mat work area in millimetres. */
data class PlotterModel(
    val id: Int,
    val name: String,
    val matWidthMm: Double,
    val matHeightMm: Double,
)

object Devices {
    // Work-area sizes are sensible starting defaults; they only affect the placement mat, not the
    // cut coordinates, and can be adjusted later once measured on the real machine.
    val models = listOf(
        PlotterModel(1, "Smart1", matWidthMm = 300.0, matHeightMm = 600.0),
        PlotterModel(2, "Smart2", matWidthMm = 300.0, matHeightMm = 600.0),
        PlotterModel(3, "Smart3", matWidthMm = 300.0, matHeightMm = 600.0),
        PlotterModel(4, "Smart4", matWidthMm = 300.0, matHeightMm = 600.0),
    )

    val default = models.first()

    /** Guess the model from a Bluetooth device name (e.g. "Smart1-xxxx"). */
    fun matchByName(btName: String?): PlotterModel? {
        if (btName == null) return null
        return models.firstOrNull { btName.contains(it.name, ignoreCase = true) }
    }
}
