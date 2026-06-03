package de.knutwurst.knutcut.data

import android.content.Context

/** Persists the user's choices (material, tool, force, mat, last device) across app restarts. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("knutcut", Context.MODE_PRIVATE)

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

    var matName: String?
        get() = p.getString("matName", null)
        set(v) = p.edit().putString("matName", v).apply()
}
