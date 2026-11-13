package com.sandhyagems.app.data

import com.sandhyagems.app.StoreConfig

/** Small helpers for Shopify URL conventions. */
object Shopify {
    private val WIDTHS = intArrayOf(160, 320, 480, 640, 800, 1080, 1440)
    private val WIDTH_PARAM = Regex("([?&])width=\\d+&?")
    private val ORDER_PATH = Regex("/orders/[0-9a-zA-Z]{16,}")

    /**
     * Asks Shopify's image CDN for a picture no wider than needed, so a product
     * card downloads a few kilobytes instead of the full-size photograph.
     * Widths are rounded up to a few fixed sizes so the cache is reused.
     */
    fun sizedImage(url: String?, widthPx: Int): String? {
        var u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        if (u.startsWith("//")) u = "https:$u"
        if (u.startsWith("http://")) u = "https://" + u.substring(7)
        if (!u.contains("cdn.shopify.com") && !u.contains("/cdn/shop/")) return u
        val bucket = WIDTHS.firstOrNull { it >= widthPx } ?: WIDTHS.last()
        u = u.replace(WIDTH_PARAM, "$1").trimEnd('?', '&')
        return u + (if (u.contains('?')) "&" else "?") + "width=" + bucket
    }

    /**
     * A Shopify "cart permalink": opening it fills the checkout with exactly
     * these variants and quantities.
     */
    fun checkoutUrl(items: List<Pair<Long, Int>>): String =
        StoreConfig.BASE_URL + "/cart/" + items.joinToString(",") { (id, qty) -> "$id:$qty" }

    /** True once the customer has reached the order confirmation page. */
    fun isOrderComplete(url: String?): Boolean {
        val path = url?.substringAfter("://", "")?.substringAfter('/', "")?.substringBefore('?')?.let { "/$it" }
            ?: return false
        // Looking at an old order in "My account" is not a new purchase.
        if (path.startsWith("/account")) return false
        return path.contains("/thank_you") || path.contains("/thank-you") || ORDER_PATH.containsMatchIn(path)
    }

    fun isStoreHost(host: String?): Boolean = host != null && host.lowercase() in StoreConfig.STORE_HOSTS
}
