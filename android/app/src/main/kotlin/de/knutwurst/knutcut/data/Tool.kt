package de.knutwurst.knutcut.data

/**
 * A tool holder on the plotter. The Smart 1 has two; `SP<sp>` selects which one cuts/draws.
 * Tool 1 is the right/master holder (the knife), tool 2 the left/slave holder (the pen) — matching
 * the stock app, which cuts with `SP1`.
 */
enum class Tool(val sp: Int, val label: String) {
    KNIFE(1, "Messer (rechts)"),
    PEN(2, "Stift (links)"),
}
