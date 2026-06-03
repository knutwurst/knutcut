package de.knutwurst.knutcut.data

/** A material preset. [force] is the FS pressure value the machine cuts at. */
data class Material(val id: String, val name: String, val force: Int)

/** Material presets from my list in the cutter cloud (pressure = FS force), de-duplicated by name. */
object Materials {
    const val FORCE_MIN = 10
    const val FORCE_MAX = 350

    val presets: List<Material> = listOf(
        Material(id = "ffa9a045-e01e-4544-8472-1333e04f36ca", name = "Gloss photo paper (210g)", force = 220),
        Material(id = "f8dabf8d-f7a3-4ddc-80ad-5aea9f39980b", name = "Black heat transfer film", force = 180),
        Material(id = "ea07339c-b4b0-42c4-846e-9ba7a7600b9d", name = "Transfer film", force = 190),
        Material(id = "e85d2897-ed90-42af-a271-ca8604bd58d7", name = "A4 printing paper", force = 180),
        Material(id = "e7a1986d-f5c2-404d-8da6-69c44ed5c50a", name = "Gloss film", force = 200),
        Material(id = "dd5dc4fa-a939-40e3-8b32-b09ee472a1a9", name = "Kraft paper (150g)", force = 200),
        Material(id = "dc4aaa62-46b3-4bb6-ba22-809b8cfdffa1", name = "Both side coated art paper (130g)", force = 170),
        Material(id = "c75ef1a6-6a11-4384-b899-405bd7b70973", name = "Dermatoglyph paper (150g)", force = 200),
        Material(id = "bc857863-4da9-4fff-9706-8e333ab84766", name = "Dermatoglyph paper (230g)", force = 230),
        Material(id = "aee1e1f0-680c-4b4d-8d26-e5445a367b61", name = "Both side coated art paper (250g)", force = 260),
        Material(id = "aecca889-33fa-4223-8597-8375e7936e1e", name = "Papercard (250g)", force = 270),
        Material(id = "ae1a84b6-4644-4b0f-a403-4569ae90b434", name = "Flash heat trasfer vinyl", force = 220),
        Material(id = "ac7b427e-5053-4409-961b-a084f57c7169", name = "Transfer sheet", force = 240),
        Material(id = "987927b8-1ffd-4979-8f71-c546b0f71301", name = "Pearlescent vinyl", force = 190),
        Material(id = "950d2e3c-5416-4da5-8034-6bc9c9136091", name = "Coloured papercard (200g)", force = 210),
        Material(id = "91d57a61-c944-40b4-9a34-f8f969c77896", name = "Dermatoglyph paper (100g)", force = 190),
        Material(id = "8847e3c6-9ee5-461f-a9ad-5e6ee32486bd", name = "Kraft paper (250g)", force = 260),
        Material(id = "83cfabca-313f-4075-a8c3-58bdea2300ec", name = "Gloss photo paper (230g)", force = 250),
        Material(id = "7aff92f8-598d-41ba-b778-47bdefa5afac", name = "Sticker papercard", force = 240),
        Material(id = "6f287cff-fb2c-48e9-a7b9-42376ba1f65b", name = "Laser heat transfer vinyl", force = 240),
        Material(id = "6ccf171c-7d66-4372-aecd-9cea5b3a4e8a", name = "Kraft paper (100g)", force = 180),
        Material(id = "669ee240-3f0c-452d-a5fe-045314f6aced", name = "Both side coated art paper (200g)", force = 220),
        Material(id = "6118c496-4970-49e8-9823-4643c99670a8", name = "Matte film", force = 180),
        Material(id = "3bc7039f-e460-4600-81e7-cfcb1bb3bcb2", name = "Papercard (300g)", force = 300),
        Material(id = "301de468-3528-47d8-a78d-ebb8574e6c8d", name = "Coloured heat transfer vinyl", force = 190),
        Material(id = "2a9d8aa8-38d4-463b-9764-dd6e9c9f7e75", name = "Matte gold heat transfer vinyl", force = 210),
        Material(id = "2a91f598-99b5-40e1-ae90-60667ab8bf9e", name = "Hard plastic film", force = 260),
        Material(id = "1c2dc5dc-ef94-4546-9922-b1909c39386e", name = "Normal vinyl", force = 180),
        Material(id = "0ace073d-f6df-44ca-be29-ae69ff7ffffc", name = "Flash papercard", force = 280),
    )

    val default = presets.first()
}
