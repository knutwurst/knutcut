package de.knutwurst.knutcut.data

import android.content.Context

/** Theme override: follow Android, or force light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Mat preview colour mode: tool-coloured outlines only, or the original SVG colours. */
enum class ColorMode { OUTLINE, COLOR }

private const val KEY_MATERIALS_JSON = "customMaterialsJson"

/** Persists the user's choices (material, tool, force, mat, last device, theme) across app restarts. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("knutcut", Context.MODE_PRIVATE)
    // The paired-device address lives in its own prefs file so it can be excluded from cloud backup
    // (it's specific to this phone + plotter pairing and pointless to restore elsewhere).
    private val devicePrefs = context.getSharedPreferences("knutcut_device", Context.MODE_PRIVATE)

    init {
        // One-time migrations from older storage formats.
        if (p.contains("deviceAddress")) {
            devicePrefs.edit().putString("deviceAddress", p.getString("deviceAddress", null)).apply()
            p.edit().remove("deviceAddress").apply()
        }
        if (!p.contains(KEY_MATERIALS_JSON) && p.contains("customMaterials")) {
            val legacy = (p.getString("customMaterials", "") ?: "").split(REC).filter { it.isNotBlank() }.mapNotNull {
                val f = it.split(FIELD)
                if (f.size == 3) Material(f[0], f[1], f[2].toIntOrNull() ?: 100) else null
            }
            p.edit().putString(KEY_MATERIALS_JSON, materialsToJson(legacy)).remove("customMaterials").apply()
        }
    }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(p.getString("theme", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = p.edit().putString("theme", v.name).apply()

    var colorMode: ColorMode
        get() = runCatching { ColorMode.valueOf(p.getString("colorMode", ColorMode.COLOR.name)!!) }.getOrDefault(ColorMode.COLOR)
        set(v) = p.edit().putString("colorMode", v.name).apply()

    var deviceAddress: String?
        get() = devicePrefs.getString("deviceAddress", null)
        set(v) = devicePrefs.edit().putString("deviceAddress", v).apply()

    /** Transport of the last connected device ("SPP"/"BLE"), so auto-reconnect picks the right path. */
    var deviceTransport: String?
        get() = devicePrefs.getString("deviceTransport", null)
        set(v) = devicePrefs.edit().putString("deviceTransport", v).apply()

    /** The plotter model the user selected (decides the load/start gates and the name shown). */
    var modelId: Int
        get() = p.getInt("modelId", 1)
        set(v) = p.edit().putInt("modelId", v).apply()

    var materialId: String?
        get() = p.getString("materialId", null)
        set(v) = p.edit().putString("materialId", v).apply()

    var toolSp: Int
        get() = p.getInt("toolSp", 1)
        set(v) = p.edit().putInt("toolSp", v).apply()

    var force: Int
        get() = p.getInt("force", -1)
        set(v) = p.edit().putInt("force", v).apply()

    /** Pen pressure (separate from the material's knife pressure; a pen needs only light contact). */
    var penForce: Int
        get() = p.getInt("penForce", 15)
        set(v) = p.edit().putInt("penForce", v).apply()

    var matName: String?
        get() = p.getString("matName", null)
        set(v) = p.edit().putString("matName", v).apply()

    /** Top origin offset in mm: extra space above the 12" grid start. 0 = plotter handles it. */
    var originOffsetMm: Int
        get() = p.getInt("originOffsetMm", 0)
        set(v) = p.edit().putInt("originOffsetMm", v).apply()

    /** Check for a new version on launch. On by default. */
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()

    /** Reorder contours by nearest-neighbour to shorten travel. Off by default (changes the path). */
    var optimizeCutOrder: Boolean
        get() = p.getBoolean("optimizeCutOrder", false)
        set(v) = p.edit().putBoolean("optimizeCutOrder", v).apply()

    /** Silhouette cut speed (GPGL "!" command); clamped per family at cut time. */
    var silhouetteSpeed: Int
        get() = p.getInt("silhouetteSpeed", 10)
        set(v) = p.edit().putInt("silhouetteSpeed", v).apply()

    var displayUnit: DisplayUnit
        get() = runCatching { DisplayUnit.valueOf(p.getString("displayUnit", DisplayUnit.MM.name)!!) }.getOrDefault(DisplayUnit.MM)
        set(v) = p.edit().putString("displayUnit", v.name).apply()

    /** Grid snap step in mm while dragging on the mat (0 = off). */
    var snapMm: Float
        get() = p.getFloat("snapMm", 0f)
        set(v) = p.edit().putFloat("snapMm", v).apply()

    /** Smart alignment guides: snap a dragged layer's centre to another layer's (or the mat's) centre. */
    var alignGuides: Boolean
        get() = p.getBoolean("alignGuides", true)
        set(v) = p.edit().putBoolean("alignGuides", v).apply()

    /** Material ids used in an actual plot, most recent first (max 5). */
    var recentMaterialIds: List<String>
        get() = (p.getString("recentMaterials", "") ?: "").split(REC).filter { it.isNotBlank() }
        set(v) = p.edit().putString("recentMaterials", v.joinToString(REC)).apply()

    /** Drag-knife corner/closure compensation (the stock app's pltFixUtils). On by default. */
    var dragKnifeComp: Boolean
        get() = p.getBoolean("dragKnifeComp", true)
        set(v) = p.edit().putBoolean("dragKnifeComp", v).apply()

    /** Blade offset for the drag-knife compensation, in plotter units (40/mm). */
    var bladeOffset: Int
        get() = p.getInt("bladeOffset", 13)
        set(v) = p.edit().putInt("bladeOffset", v).apply()

    /** User-defined materials, stored as JSON (migrated once from the old control-char format). */
    var customMaterials: List<Material>
        get() = materialsFromJson(p.getString(KEY_MATERIALS_JSON, null))
        set(v) = p.edit().putString(KEY_MATERIALS_JSON, materialsToJson(v)).apply()

    private fun materialsToJson(list: List<Material>): String {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("id", it.id).put("name", it.name).put("force", it.force)) }
        return arr.toString()
    }

    private fun materialsFromJson(s: String?): List<Material> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) null else Material(id, o.optString("name"), o.optInt("force", 100))
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val REC = "\u001e"
        const val FIELD = "\u001f"
    }
}
