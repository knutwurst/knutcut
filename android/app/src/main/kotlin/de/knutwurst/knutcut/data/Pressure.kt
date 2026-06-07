package de.knutwurst.knutcut.data

/** Pressure scale conversions between the plotter families. */
object Pressure {
    /** Map a VEVOR FS force ([Materials.FORCE_MIN]..[Materials.FORCE_MAX]) onto the Silhouette
     *  pressure scale (1..33), so the same material setting gives a comparable cut on both. */
    fun silhouette(force: Int): Int {
        val span = (Materials.FORCE_MAX - Materials.FORCE_MIN).toDouble()
        val frac = (force - Materials.FORCE_MIN).coerceAtLeast(0) / span
        return (Math.round(frac * 32) + 1).toInt().coerceIn(1, 33)
    }
}
