package de.knutwurst.knutcut.data

/**
 * A tool holder on the plotter. The Smart 1 has two; `SP<sp>` selects which one cuts/draws.
 * Tool 1 is the right/master holder (the knife), tool 2 the left/slave holder (the pen) — matching
 * the stock app, which cuts with `SP1`. [action] labels the main button; [progress] the running state.
 */
enum class Tool(val sp: Int, val label: String, val action: String, val progress: String) {
    KNIFE(1, "Messer (rechts)", "Schneiden", "Schneide…"),
    PEN(2, "Stift (links)", "Zeichnen", "Zeichne…"),
}
