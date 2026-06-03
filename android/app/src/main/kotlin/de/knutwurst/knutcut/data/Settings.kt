package de.knutwurst.knutcut.data

import android.content.Context

/** Theme override: follow Android, or force light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Persists the user's choices (material, tool, force, mat, last device, theme) across app restarts. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("knutcut", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(p.getString("theme", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = p.edit().putString("theme", v.name).apply()

    var deviceAddress: String?
        get() = p.getString("deviceAddress", null)
        set(v) = p.edit().putString("deviceAddress", v).apply()

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
        get() = p.getInt("penForce", 40)
        set(v) = p.edit().putInt("penForce", v).apply()

    var matName: String?
        get() = p.getString("matName", null)
        set(v) = p.edit().putString("matName", v).apply()

    /** Drag-knife corner/closure compensation (the stock app's pltFixUtils). On by default. */
    var dragKnifeComp: Boolean
        get() = p.getBoolean("dragKnifeComp", true)
        set(v) = p.edit().putBoolean("dragKnifeComp", v).apply()

    /** Blade offset for the drag-knife compensation, in plotter units (40/mm). */
    var bladeOffset: Int
        get() = p.getInt("bladeOffset", 13)
        set(v) = p.edit().putInt("bladeOffset", v).apply()

    /** User-defined materials, stored as id/name/force records using control-char separators. */
    var customMaterials: List<Material>
        get() = (p.getString("customMaterials", "") ?: "").split(REC).filter { it.isNotBlank() }.mapNotNull {
            val f = it.split(FIELD)
            if (f.size == 3) Material(f[0], f[1], f[2].toIntOrNull() ?: 100) else null
        }
        set(v) = p.edit().putString(
            "customMaterials",
            v.joinToString(REC) { "${it.id}$FIELD${it.name}$FIELD${it.force}" },
        ).apply()

    private companion object {
        const val REC = "\u001e"
        const val FIELD = "\u001f"
    }
}
