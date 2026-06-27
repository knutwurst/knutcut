package de.knutwurst.knutcut.data

/**
 * Describes the text that a layer was generated from, so the curve can be re-applied without
 * losing the original glyphs.
 *
 * [text]      – the string, newlines stripped (curved text is always single-line).
 * [fontIndex] – index into [de.knutwurst.knutcut.ui.FontRepository.options].
 * [heightMm]  – requested capital height in millimeters.
 * [curve]     – slider value in -100..100 (0 = straight, maps to TextArc curve / 100.0).
 */
data class TextSpec(
    val text: String,
    val fontIndex: Int,
    val heightMm: Double,
    val curve: Int = 0,
)
