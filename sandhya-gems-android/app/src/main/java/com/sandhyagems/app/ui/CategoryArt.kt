package com.sandhyagems.app.ui

import com.sandhyagems.app.R

/**
 * Sandhya Gems' own photographs, built into the app. They are shown for a
 * category that has no picture set in the store, so no category is ever blank.
 * Product photos themselves always come live from the store.
 */
object CategoryArt {
    // Checked in order, so "blue zircon" must come before "zircon".
    private val photos = listOf(
        listOf("ruby", "manik") to R.drawable.gem_ruby,
        listOf("emerald", "panna") to R.drawable.gem_emerald,
        listOf("coral", "moonga") to R.drawable.gem_coral,
        listOf("blue zircon") to R.drawable.gem_blue_zircon,
        listOf("zircon") to R.drawable.gem_zircon,
        listOf("gomed", "hessonite") to R.drawable.gem_gomed,
        listOf("opal") to R.drawable.gem_opal,
        listOf("pearl", "moti") to R.drawable.gem_pearl,
        listOf("amethyst") to R.drawable.gem_amethyst,
        listOf("citrine", "sunela") to R.drawable.gem_citrine,
    )

    /** The photo for a category name such as "Natural Ruby (Manik)", or null if none fits. */
    fun forTitle(title: String): Int? {
        val name = title.lowercase()
        return photos.firstOrNull { (words, _) -> words.any { it in name } }?.second
    }
}
