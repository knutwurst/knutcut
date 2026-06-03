package de.knutwurst.knutcut.data

/** Length unit for the editor's readouts and inputs. Geometry stays in mm internally. */
enum class DisplayUnit(val label: String, val mmPerUnit: Double) {
    MM("mm", 1.0),
    CM("cm", 10.0),
    INCH("in", 25.4);

    fun fromMm(mm: Double): Double = mm / mmPerUnit
    fun toMm(value: Double): Double = value * mmPerUnit
}
