package de.knutwurst.knutcut.data

/**
 * A tool holder on the plotter. The Smart 1 has two; `SP<sp>` selects which one cuts/draws.
 * Tool 1 is the right/master holder (the knife), tool 2 the left/slave holder (the pen), matching
 * the stock app, which cuts with `SP1`. UI labels live in string resources (ui_knife/ui_pen,
 * ui_cut/ui_draw/ui_plot), so the tool itself carries only the protocol selector.
 */
enum class Tool(val sp: Int) {
    KNIFE(1),
    PEN(2),
}
