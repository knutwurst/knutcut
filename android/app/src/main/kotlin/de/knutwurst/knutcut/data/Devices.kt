package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.LinkTransport
import de.knutwurst.knutcut.svgcore.SilhouetteDevice
import de.knutwurst.knutcut.svgcore.SilhouetteFamily

/** Which transport + protocol stack the model uses. */
enum class PlotterFamily {
    SPP_VEVOR,
    BLE_SILHOUETTE;

    /** The one transport this family's protocol may run over (VEVOR = SPP, Silhouette = BLE). */
    val transport: LinkTransport
        get() = when (this) {
            SPP_VEVOR -> LinkTransport.SPP
            BLE_SILHOUETTE -> LinkTransport.BLE
        }

    /**
     * True if a link on [linkTransport] may carry this family's protocol. A cut must only proceed
     * when this holds, so VEVOR JSON+HPGL is never streamed over a BLE Silhouette link (or vice
     * versa).
     */
    fun matches(linkTransport: LinkTransport): Boolean = linkTransport == transport
}

/**
 * A plotter model. VEVOR models use Bluetooth Classic SPP + JSON+HPGL; Silhouette models use BLE
 * GATT + GPGL. The [family] field selects the stack; [silhouetteDevice] carries Silhouette-specific
 * geometry (machine width/length in mm and command-generation family).
 *
 * The model can't be told from the Bluetooth name (which is generic, e.g. "Smart-BFA8"), so — like
 * the stock app — the user picks it. For VEVOR, that choice decides the load/start gates. For
 * Silhouette, it decides the GPGL setup sequence and boundary dimensions.
 */
data class PlotterModel(
    val modelId: Int,
    val name: String,                               // internal catalog name, e.g. "Smart1"
    val displayName: String,                        // shown to the user, e.g. "VEVOR Smart 1"
    val knifeCount: Int,
    val hasPaperKey: Boolean,
    val hasStartKey: Boolean,
    val family: PlotterFamily = PlotterFamily.SPP_VEVOR,
    val silhouetteDevice: SilhouetteDevice? = null, // non-null for BLE_SILHOUETTE models
)

object Devices {
    val models = listOf(
        // ── VEVOR Smart (Bluetooth Classic SPP / JSON+HPGL) ──────────────────────────────────
        PlotterModel(1, "Smart1", "VEVOR Smart 1", knifeCount = 2, hasPaperKey = true,  hasStartKey = true),
        PlotterModel(2, "Smart2", "VEVOR Smart 2", knifeCount = 2, hasPaperKey = true,  hasStartKey = true),
        PlotterModel(3, "Smart3", "VEVOR Smart 3", knifeCount = 2, hasPaperKey = false, hasStartKey = false),
        PlotterModel(4, "Smart4", "VEVOR Smart 4", knifeCount = 2, hasPaperKey = false, hasStartKey = false),
        // ── Silhouette (Bluetooth LE / GPGL) ─────────────────────────────────────────────────
        PlotterModel(10, "Cameo3",    "Silhouette Cameo 3",    knifeCount = 1, hasPaperKey = false, hasStartKey = false,
            family = PlotterFamily.BLE_SILHOUETTE,
            silhouetteDevice = SilhouetteDevice("Cameo 3",    SilhouetteFamily.CAMEO3,    304.8, 3000.0)),
        PlotterModel(11, "Cameo4",    "Silhouette Cameo 4",    knifeCount = 1, hasPaperKey = false, hasStartKey = false,
            family = PlotterFamily.BLE_SILHOUETTE,
            silhouetteDevice = SilhouetteDevice("Cameo 4",    SilhouetteFamily.CAMEO4_LINE, 304.8, 3000.0)),
        PlotterModel(12, "Cameo5",    "Silhouette Cameo 5",    knifeCount = 1, hasPaperKey = false, hasStartKey = false,
            family = PlotterFamily.BLE_SILHOUETTE,
            silhouetteDevice = SilhouetteDevice("Cameo 5",    SilhouetteFamily.CAMEO4_LINE, 330.2, 3000.0)),
        PlotterModel(13, "Portrait2", "Silhouette Portrait 2", knifeCount = 1, hasPaperKey = false, hasStartKey = false,
            family = PlotterFamily.BLE_SILHOUETTE,
            silhouetteDevice = SilhouetteDevice("Portrait 2", SilhouetteFamily.LEGACY,    203.0, 3000.0)),
        PlotterModel(14, "Portrait3", "Silhouette Portrait 3", knifeCount = 1, hasPaperKey = false, hasStartKey = false,
            family = PlotterFamily.BLE_SILHOUETTE,
            silhouetteDevice = SilhouetteDevice("Portrait 3", SilhouetteFamily.CAMEO4_LINE, 203.0, 18290.0)),
    )

    /** VEVOR-only models — shown in the VEVOR section of the device dialog. */
    val vevorModels: List<PlotterModel> = models.filter { it.family == PlotterFamily.SPP_VEVOR }

    /** Silhouette-only models — shown in the Silhouette section of the device dialog. */
    val silhouetteModels: List<PlotterModel> = models.filter { it.family == PlotterFamily.BLE_SILHOUETTE }

    val default: PlotterModel = models.first()

    fun byId(id: Int): PlotterModel = models.firstOrNull { it.modelId == id } ?: default

    // Bluetooth Classic name tokens (stock app uses ["VEVOR","Smart"]; kept unchanged).
    private val NAME_TOKENS = listOf("vevor", "smart")

    // BLE name token for Silhouette cutters — Cameo/Portrait advertise "Silhouette" in their name.
    private val LE_NAME_TOKENS = listOf("silhouette")

    /** True if a Classic Bluetooth name looks like a supported VEVOR plotter. */
    fun isCompatible(btName: String?): Boolean {
        val n = btName?.lowercase() ?: return false
        return NAME_TOKENS.any { n.contains(it) }
    }

    /** True if a BLE advertisement name looks like a supported Silhouette plotter. */
    fun isCompatibleLe(btName: String?): Boolean {
        val n = btName?.lowercase() ?: return false
        return LE_NAME_TOKENS.any { n.contains(it) }
    }

    /** The best Silhouette model for a found BLE device name (e.g. "Silhouette Cameo 4" → Cameo 4),
     *  falling back to the first Silhouette model when the name doesn't identify a specific one. */
    fun modelForLe(btName: String?): PlotterModel {
        val n = btName?.lowercase() ?: return silhouetteModels.first()
        return silhouetteModels.firstOrNull { m ->
            val key = m.displayName.removePrefix("Silhouette ").lowercase()
            n.contains(key) || n.contains(key.replace(" ", ""))
        } ?: silhouetteModels.first()
    }
}
