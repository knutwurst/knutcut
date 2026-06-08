package de.knutwurst.knutcut.data

import androidx.annotation.StringRes
import de.knutwurst.knutcut.R

enum class PlotterSvgCategory(
    val id: String,
    @StringRes val labelRes: Int,
) {
    BASICS("basics", R.string.ui_lib_cat_basics),
    NATURE("nature", R.string.ui_lib_cat_nature),
    HOME("home", R.string.ui_lib_cat_home),
    PARTY("party", R.string.ui_lib_cat_party),
    LABELS("labels", R.string.ui_lib_cat_labels),
}

data class PlotterSvgItem(
    val id: String,
    val name: String,
    val category: PlotterSvgCategory,
    val tags: List<String>,
    val source: String,
    val svg: String,
) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return name.lowercase().contains(q) ||
            category.id.contains(q) ||
            tags.any { it.lowercase().contains(q) } ||
            source.lowercase().contains(q)
    }
}

object PlotterSvgLibrary {
    val items: List<PlotterSvgItem> = listOf(
        item(
            "heart",
            "Herz",
            PlotterSvgCategory.BASICS,
            "liebe love valentine valentinstag",
            path("M50 86 C18 58 12 38 25 24 C34 15 46 19 50 30 C54 19 66 15 75 24 C88 38 82 58 50 86 Z"),
        ),
        item(
            "star",
            "Stern",
            PlotterSvgCategory.BASICS,
            "stern star weihnachten",
            """<polygon points="50,8 61,37 92,38 67,57 76,88 50,70 24,88 33,57 8,38 39,37"/>""",
        ),
        item(
            "crown",
            "Krone",
            PlotterSvgCategory.BASICS,
            "krone crown prinzessin",
            path("M14 76 L20 32 L38 55 L50 20 L62 55 L80 32 L86 76 Z M18 84 H82"),
        ),
        item(
            "arrow",
            "Pfeil",
            PlotterSvgCategory.BASICS,
            "pfeil arrow richtung",
            path("M10 50 H64 V30 L92 50 L64 70 V50 Z"),
        ),
        item(
            "flower",
            "Blume",
            PlotterSvgCategory.NATURE,
            "blume flower natur",
            """
                <ellipse cx="50" cy="23" rx="13" ry="20"/>
                <ellipse cx="50" cy="77" rx="13" ry="20"/>
                <ellipse cx="23" cy="50" rx="20" ry="13"/>
                <ellipse cx="77" cy="50" rx="20" ry="13"/>
                <ellipse cx="31" cy="31" rx="13" ry="20" transform="rotate(-45 31 31)"/>
                <ellipse cx="69" cy="31" rx="13" ry="20" transform="rotate(45 69 31)"/>
                <ellipse cx="31" cy="69" rx="13" ry="20" transform="rotate(45 31 69)"/>
                <ellipse cx="69" cy="69" rx="13" ry="20" transform="rotate(-45 69 69)"/>
                <circle cx="50" cy="50" r="13"/>
            """.trimIndent(),
        ),
        item(
            "leaf",
            "Blatt",
            PlotterSvgCategory.NATURE,
            "blatt leaf natur pflanze",
            path("M17 72 C28 24 65 12 87 15 C84 48 63 77 24 82 M23 79 C43 58 59 42 78 23"),
        ),
        item(
            "butterfly",
            "Schmetterling",
            PlotterSvgCategory.NATURE,
            "schmetterling butterfly natur",
            """
                <path d="M49 43 C35 12 9 12 11 38 C12 58 31 60 45 51"/>
                <path d="M51 43 C65 12 91 12 89 38 C88 58 69 60 55 51"/>
                <path d="M47 54 C28 58 17 82 36 88 C49 92 52 68 50 56"/>
                <path d="M53 54 C72 58 83 82 64 88 C51 92 48 68 50 56"/>
                <ellipse cx="50" cy="52" rx="5" ry="25"/>
                <path d="M48 30 C42 20 36 15 29 12 M52 30 C58 20 64 15 71 12"/>
            """.trimIndent(),
        ),
        item(
            "snowflake",
            "Schneeflocke",
            PlotterSvgCategory.NATURE,
            "schnee snowflake winter",
            """
                <line x1="50" y1="9" x2="50" y2="91"/>
                <line x1="14" y1="29" x2="86" y2="71"/>
                <line x1="14" y1="71" x2="86" y2="29"/>
                <path d="M38 18 L50 29 L62 18 M38 82 L50 71 L62 82"/>
                <path d="M20 42 L35 46 L31 31 M80 58 L65 54 L69 69"/>
                <path d="M20 58 L35 54 L31 69 M80 42 L65 46 L69 31"/>
            """.trimIndent(),
        ),
        item(
            "house",
            "Haus",
            PlotterSvgCategory.HOME,
            "haus home zuhause",
            """
                <path d="M12 48 L50 17 L88 48"/>
                <path d="M22 45 V84 H78 V45"/>
                <rect x="42" y="59" width="16" height="25"/>
                <rect x="28" y="54" width="12" height="12"/>
                <rect x="60" y="54" width="12" height="12"/>
            """.trimIndent(),
        ),
        item(
            "paw",
            "Pfote",
            PlotterSvgCategory.HOME,
            "pfote paw hund katze tier",
            """
                <ellipse cx="35" cy="28" rx="10" ry="14"/>
                <ellipse cx="65" cy="28" rx="10" ry="14"/>
                <ellipse cx="21" cy="49" rx="10" ry="13"/>
                <ellipse cx="79" cy="49" rx="10" ry="13"/>
                <path d="M50 45 C30 45 22 66 29 78 C35 88 44 80 50 80 C56 80 65 88 71 78 C78 66 70 45 50 45 Z"/>
            """.trimIndent(),
        ),
        item(
            "mug",
            "Tasse",
            PlotterSvgCategory.HOME,
            "tasse cup kaffee coffee",
            """
                <path d="M22 36 H64 V67 C64 77 56 84 43 84 C30 84 22 77 22 67 Z"/>
                <path d="M64 45 H73 C84 45 84 68 64 67"/>
                <path d="M31 22 C26 16 34 13 29 8 M48 22 C43 16 51 13 46 8 M62 22 C57 16 65 13 60 8"/>
            """.trimIndent(),
        ),
        item(
            "camera",
            "Kamera",
            PlotterSvgCategory.HOME,
            "kamera camera foto",
            """
                <rect x="14" y="30" width="72" height="52" rx="5"/>
                <path d="M34 30 L41 20 H59 L66 30"/>
                <circle cx="50" cy="56" r="16"/>
                <circle cx="73" cy="40" r="4"/>
            """.trimIndent(),
        ),
        item(
            "music",
            "Musiknote",
            PlotterSvgCategory.HOME,
            "musik note music",
            """
                <path d="M58 15 V68"/>
                <path d="M58 15 L80 22 V35 L58 28"/>
                <ellipse cx="42" cy="72" rx="16" ry="11" transform="rotate(-20 42 72)"/>
            """.trimIndent(),
        ),
        item(
            "tree",
            "Tanne",
            PlotterSvgCategory.PARTY,
            "tanne baum christmas weihnachten",
            """
                <path d="M50 9 L26 39 H39 L19 64 H36 L27 78 H73 L64 64 H81 L61 39 H74 Z"/>
                <rect x="43" y="78" width="14" height="13"/>
            """.trimIndent(),
        ),
        item(
            "pumpkin",
            "Kürbis",
            PlotterSvgCategory.PARTY,
            "kuerbis kürbis pumpkin halloween",
            """
                <path d="M50 22 C72 22 88 37 88 58 C88 78 72 90 50 90 C28 90 12 78 12 58 C12 37 28 22 50 22 Z"/>
                <ellipse cx="50" cy="58" rx="19" ry="35"/>
                <path d="M50 22 C47 12 53 8 62 13"/>
            """.trimIndent(),
        ),
        item(
            "gift",
            "Geschenk",
            PlotterSvgCategory.PARTY,
            "geschenk gift party",
            """
                <rect x="16" y="39" width="68" height="48"/>
                <path d="M50 39 V87 M16 56 H84"/>
                <path d="M48 38 C30 38 27 20 39 18 C48 17 50 38 50 38"/>
                <path d="M52 38 C70 38 73 20 61 18 C52 17 50 38 50 38"/>
            """.trimIndent(),
        ),
        item(
            "balloon",
            "Ballon",
            PlotterSvgCategory.PARTY,
            "ballon balloon geburtstag birthday",
            """
                <ellipse cx="50" cy="35" rx="26" ry="30"/>
                <path d="M44 64 L56 64 L50 74 Z"/>
                <path d="M50 74 C34 83 68 89 50 97"/>
            """.trimIndent(),
        ),
        item(
            "rabbit",
            "Hase",
            PlotterSvgCategory.PARTY,
            "hase rabbit ostern easter",
            """
                <ellipse cx="39" cy="29" rx="10" ry="27" transform="rotate(-18 39 29)"/>
                <ellipse cx="61" cy="29" rx="10" ry="27" transform="rotate(18 61 29)"/>
                <circle cx="50" cy="61" r="27"/>
                <circle cx="39" cy="56" r="3"/>
                <circle cx="61" cy="56" r="3"/>
                <path d="M43 68 Q50 76 57 68"/>
            """.trimIndent(),
        ),
        item(
            "round-tag",
            "Rundes Etikett",
            PlotterSvgCategory.LABELS,
            "etikett tag label rund",
            """
                <circle cx="50" cy="50" r="40"/>
                <circle cx="50" cy="20" r="5"/>
            """.trimIndent(),
        ),
        item(
            "scallop-label",
            "Zackenkreis",
            PlotterSvgCategory.LABELS,
            "etikett label scallop zacken",
            """<polygon points="50,7 58,18 71,14 75,27 88,31 82,43 93,50 82,57 88,69 75,73 71,86 58,82 50,93 42,82 29,86 25,73 12,69 18,57 7,50 18,43 12,31 25,27 29,14 42,18"/>""",
        ),
        item(
            "banner",
            "Banner",
            PlotterSvgCategory.LABELS,
            "banner label schild",
            path("M10 28 H90 L78 50 L90 72 H10 L22 50 Z"),
        ),
        item(
            "gift-tag",
            "Anhänger",
            PlotterSvgCategory.LABELS,
            "anhaenger anhänger tag label",
            """
                <path d="M23 12 H67 L88 33 V88 H23 Z"/>
                <circle cx="64" cy="31" r="5"/>
            """.trimIndent(),
        ),
        item(
            "frame",
            "Rahmen",
            PlotterSvgCategory.LABELS,
            "rahmen frame karte",
            """
                <rect x="14" y="18" width="72" height="64" rx="6"/>
                <rect x="25" y="29" width="50" height="42" rx="3"/>
            """.trimIndent(),
        ),
    )

    val categories: List<PlotterSvgCategory> = PlotterSvgCategory.entries

    private fun item(
        id: String,
        name: String,
        category: PlotterSvgCategory,
        tags: String,
        body: String,
        source: String = "Knutwurst",
    ): PlotterSvgItem =
        PlotterSvgItem(
            id = id,
            name = name,
            category = category,
            tags = tags.split(" ").filter { it.isNotBlank() },
            source = source,
            svg = svg(body),
        )

    private fun svg(body: String): String =
        """<svg xmlns="http://www.w3.org/2000/svg" width="80mm" height="80mm" viewBox="0 0 100 100" fill="none" stroke="#111111" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">$body</svg>"""

    private fun path(d: String): String = """<path d="$d"/>"""
}
