package com.memoria.idedikate.model

object MemorialCatalog {
    val items = listOf(
        MemorialItemDef(
            id = "plaque",
            name = "Memorial plaque",
            geometryDescription = "Thin box + photo texture on front face",
            referenceSize = "0.22 × 0.28 × 0.015 m",
            anchorType = "WGS84"
        ),
        MemorialItemDef(
            id = "incense_stick",
            name = "Incense stick",
            geometryDescription = "Thin cylinder, tapered tip, optional ember",
            referenceSize = "Ø0.003 × 0.25 m",
            anchorType = "child"
        ),
        MemorialItemDef(
            id = "incense_pot",
            name = "Incense pot",
            geometryDescription = "Cylinder body + lip + rim, sand fill",
            referenceSize = "Ø0.10 × 0.07 m",
            anchorType = "child"
        ),
        MemorialItemDef(
            id = "incense_box",
            name = "Incense box",
            geometryDescription = "Rounded box with lid seam",
            referenceSize = "0.15 × 0.04 × 0.06 m",
            anchorType = "child"
        ),
        MemorialItemDef(
            id = "incense_paper",
            name = "Incense paper",
            geometryDescription = "Flat plane, subtle curl",
            referenceSize = "0.10 × 0.10 m",
            anchorType = "child"
        ),
        MemorialItemDef(
            id = "fruit_offering",
            name = "Fruit offering",
            geometryDescription = "Sphere or lathe, 3 fruit clustered",
            referenceSize = "Ø0.07 m each",
            anchorType = "child"
        ),
        MemorialItemDef(
            id = "food_offering",
            name = "Food offering",
            geometryDescription = "Shallow bowl + dome",
            referenceSize = "Ø0.12 m",
            anchorType = "child"
        ),
        MemorialItemDef(
            id = "candle",
            name = "Candle",
            geometryDescription = "Cylinder + small flame cone",
            referenceSize = "Ø0.02 × 0.15 m",
            anchorType = "child"
        ),
        MemorialItemDef(
            id = "flower",
            name = "Flower",
            geometryDescription = "5 petals + stem cylinder",
            referenceSize = "Ø0.06 m",
            anchorType = "child"
        )
    )
}
