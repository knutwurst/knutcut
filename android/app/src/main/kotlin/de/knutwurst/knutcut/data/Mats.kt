package de.knutwurst.knutcut.data

/** A cutting mat / work area in millimetres. The stock app offers 12×12 and 12×24 inch. */
data class Mat(val name: String, val widthMm: Double, val heightMm: Double)

object Mats {
    private const val INCH = 25.4

    val all = listOf(
        Mat("12 × 12 Zoll", 12 * INCH, 12 * INCH),
        Mat("12 × 24 Zoll", 12 * INCH, 24 * INCH),
    )

    val default = all.first()

    fun byName(name: String?): Mat? = all.firstOrNull { it.name == name }
}
