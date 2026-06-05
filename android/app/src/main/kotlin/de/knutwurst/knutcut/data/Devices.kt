package de.knutwurst.knutcut.data

/**
 * A plotter model from the stock app's catalog. The Smart1/2 have a load (paper) key and a start key,
 * so a cut must wait for both before sending the path; the Smart3/4 have neither. The model can't be
 * told from the Bluetooth name (which is generic, e.g. "Smart-BFA8"), so — like the stock app — the
 * user picks it; that choice decides the gates and the name shown.
 */
data class PlotterModel(
    val modelId: Int,
    val name: String,          // internal catalog name, e.g. "Smart1"
    val displayName: String,   // shown to the user, e.g. "VEVOR Smart 1"
    val knifeCount: Int,
    val hasPaperKey: Boolean,
    val hasStartKey: Boolean,
)

object Devices {
    val models = listOf(
        PlotterModel(1, "Smart1", "VEVOR Smart 1", knifeCount = 2, hasPaperKey = true, hasStartKey = true),
        PlotterModel(2, "Smart2", "VEVOR Smart 2", knifeCount = 2, hasPaperKey = true, hasStartKey = true),
        PlotterModel(3, "Smart3", "VEVOR Smart 3", knifeCount = 2, hasPaperKey = false, hasStartKey = false),
        PlotterModel(4, "Smart4", "VEVOR Smart 4", knifeCount = 2, hasPaperKey = false, hasStartKey = false),
    )

    val default = models.first()

    fun byId(id: Int): PlotterModel = models.firstOrNull { it.modelId == id } ?: default

    // Bluetooth name tokens that mark a compatible plotter. The stock app filters with b=["VEVOR","Smart"].
    private val NAME_TOKENS = listOf("vevor", "smart")

    /** True if a Bluetooth name looks like a supported plotter (contains "VEVOR" or "Smart"). */
    fun isCompatible(btName: String?): Boolean {
        val n = btName?.lowercase() ?: return false
        return NAME_TOKENS.any { n.contains(it) }
    }
}
