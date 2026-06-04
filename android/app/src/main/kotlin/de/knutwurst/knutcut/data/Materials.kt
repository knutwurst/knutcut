package de.knutwurst.knutcut.data

/** A material preset. [force] is the FS pressure; [nameDe] is the German display name (null for custom). */
data class Material(val id: String, val name: String, val force: Int, val nameDe: String? = null)

/** The name to show in the UI: the German name for presets, the raw name for user-defined materials. */
fun Material.display(): String = nameDe ?: name

/** Material presets from my list in the cutter cloud (pressure = FS force), de-duplicated by name. */
object Materials {
    const val FORCE_MIN = 10
    const val FORCE_MAX = 350

    val presets: List<Material> = listOf(
        Material("ffa9a045-e01e-4544-8472-1333e04f36ca", "Gloss photo paper (210g)", 220, "Glanzfotopapier (210 g)"),
        Material("f8dabf8d-f7a3-4ddc-80ad-5aea9f39980b", "Black heat transfer film", 180, "Schwarze Thermotransferfolie"),
        Material("ea07339c-b4b0-42c4-846e-9ba7a7600b9d", "Transfer film", 190, "Transferfolie"),
        Material("e85d2897-ed90-42af-a271-ca8604bd58d7", "A4 printing paper", 180, "A4-Druckpapier"),
        Material("e7a1986d-f5c2-404d-8da6-69c44ed5c50a", "Gloss film", 200, "Glanzfolie"),
        Material("dd5dc4fa-a939-40e3-8b32-b09ee472a1a9", "Kraft paper (150g)", 200, "Kraftpapier (150 g)"),
        Material("dc4aaa62-46b3-4bb6-ba22-809b8cfdffa1", "Both side coated art paper (130g)", 170, "Beidseitig gestrichenes Kunstdruckpapier (130 g)"),
        Material("c75ef1a6-6a11-4384-b899-405bd7b70973", "Dermatoglyph paper (150g)", 200, "Strukturpapier (150 g)"),
        Material("bc857863-4da9-4fff-9706-8e333ab84766", "Dermatoglyph paper (230g)", 230, "Strukturpapier (230 g)"),
        Material("aee1e1f0-680c-4b4d-8d26-e5445a367b61", "Both side coated art paper (250g)", 260, "Beidseitig gestrichenes Kunstdruckpapier (250 g)"),
        Material("aecca889-33fa-4223-8597-8375e7936e1e", "Papercard (250g)", 270, "Karton (250 g)"),
        Material("ae1a84b6-4644-4b0f-a403-4569ae90b434", "Flash heat trasfer vinyl", 220, "Glitzer-Thermotransferfolie"),
        Material("ac7b427e-5053-4409-961b-a084f57c7169", "Transfer sheet", 240, "Transferbogen"),
        Material("987927b8-1ffd-4979-8f71-c546b0f71301", "Pearlescent vinyl", 190, "Perlmuttvinyl"),
        Material("950d2e3c-5416-4da5-8034-6bc9c9136091", "Coloured papercard (200g)", 210, "Farbiger Karton (200 g)"),
        Material("91d57a61-c944-40b4-9a34-f8f969c77896", "Dermatoglyph paper (100g)", 190, "Strukturpapier (100 g)"),
        Material("8847e3c6-9ee5-461f-a9ad-5e6ee32486bd", "Kraft paper (250g)", 260, "Kraftpapier (250 g)"),
        Material("83cfabca-313f-4075-a8c3-58bdea2300ec", "Gloss photo paper (230g)", 250, "Glanzfotopapier (230 g)"),
        Material("7aff92f8-598d-41ba-b778-47bdefa5afac", "Sticker papercard", 240, "Aufkleberkarton"),
        Material("6f287cff-fb2c-48e9-a7b9-42376ba1f65b", "Laser heat transfer vinyl", 240, "Laser-Thermotransferfolie"),
        Material("6ccf171c-7d66-4372-aecd-9cea5b3a4e8a", "Kraft paper (100g)", 180, "Kraftpapier (100 g)"),
        Material("669ee240-3f0c-452d-a5fe-045314f6aced", "Both side coated art paper (200g)", 220, "Beidseitig gestrichenes Kunstdruckpapier (200 g)"),
        Material("6118c496-4970-49e8-9823-4643c99670a8", "Matte film", 180, "Mattfolie"),
        Material("3bc7039f-e460-4600-81e7-cfcb1bb3bcb2", "Papercard (300g)", 300, "Karton (300 g)"),
        Material("301de468-3528-47d8-a78d-ebb8574e6c8d", "Coloured heat transfer vinyl", 190, "Farbige Thermotransferfolie"),
        Material("2a9d8aa8-38d4-463b-9764-dd6e9c9f7e75", "Matte gold heat transfer vinyl", 210, "Matt-goldene Thermotransferfolie"),
        Material("2a91f598-99b5-40e1-ae90-60667ab8bf9e", "Hard plastic film", 260, "Hartplastikfolie"),
        Material("1c2dc5dc-ef94-4546-9922-b1909c39386e", "Normal vinyl", 180, "Normales Vinyl"),
        Material("0ace073d-f6df-44ca-be29-ae69ff7ffffc", "Flash papercard", 280, "Glitzerkarton"),
    )

    val default = presets.first()
}
