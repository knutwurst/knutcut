package de.knutwurst.knutcut.data

/**
 * A plotter model from the stock app's catalog. The Smart1/2 have a load (paper) key and a start key,
 * so a cut must wait for both before sending the path; the Smart3/4 don't.
 */
data class PlotterModel(
    val modelId: Int,
    val name: String,
    val knifeCount: Int,
    val hasPaperKey: Boolean,
    val hasStartKey: Boolean,
)

object Devices {
    val models = listOf(
        PlotterModel(1, "Smart1", knifeCount = 2, hasPaperKey = true, hasStartKey = true),
        PlotterModel(2, "Smart2", knifeCount = 2, hasPaperKey = true, hasStartKey = true),
        PlotterModel(3, "Smart3", knifeCount = 2, hasPaperKey = false, hasStartKey = false),
        PlotterModel(4, "Smart4", knifeCount = 2, hasPaperKey = false, hasStartKey = false),
    )

    val default = models.first()

    /** Map a Bluetooth name (e.g. "Smart-BFA8") to a model; defaults to Smart1 for any "Smart…" name. */
    fun matchByName(btName: String?): PlotterModel? {
        if (btName == null) return null
        return models.firstOrNull { btName.contains(it.name, ignoreCase = true) }
            ?: if (looksLikePlotter(btName)) default else null
    }

    /** The plotters advertise Bluetooth names like "Smart-BFA8". */
    fun looksLikePlotter(btName: String?): Boolean =
        btName != null && btName.trim().startsWith("Smart", ignoreCase = true)
}
