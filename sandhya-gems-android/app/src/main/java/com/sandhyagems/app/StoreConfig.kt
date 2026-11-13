package com.sandhyagems.app

/**
 * Everything specific to the store lives here. Change a value, rebuild, done.
 *
 * The app reads the catalogue from the store's public Shopify endpoints
 * (/products.json, /collections.json) and sends customers to the store's own
 * checkout, so no API key or password is stored in the app.
 */
object StoreConfig {
    const val STORE_NAME = "Sandhya Gems"
    const val BASE_URL = "https://sandhyagems.in"
    val STORE_HOSTS = setOf("sandhyagems.in", "www.sandhyagems.in")

    const val CURRENCY_SYMBOL = "₹"

    /** Collection handles shown as rows on the home screen. A row hides itself if its collection is missing or empty. */
    const val NEW_ARRIVALS_HANDLE = "new-gem"
    const val BEST_SELLERS_HANDLE = "best-seller"

    /** Contact details. */
    const val PHONE = "+919007746465"
    const val WHATSAPP_NUMBER = "919007746465" // country code + number, digits only
    const val EMAIL = "contact@sandhyagems.in"

    /** The shop, for "Get directions". The address and opening hours shown are in strings.xml. */
    const val SHOP_LATITUDE = 22.6983
    const val SHOP_LONGITUDE = 88.4371
    const val SHOP_MAP_LABEL = "Sandhya Gems Corner, New Barrackpore Post Office Market, Kolkata 700131"

    /** A product whose title contains this word is offered on the home screen as a consultation. */
    const val CONSULTATION_KEYWORD = "consultation"

    /** Pages opened inside the app. */
    const val ACCOUNT_URL = "$BASE_URL/account"
    const val ABOUT_URL = "$BASE_URL/pages/about-us"
    const val CONTACT_URL = "$BASE_URL/pages/contact"
    const val BLOG_URL = "$BASE_URL/blogs/news"
    const val SHIPPING_URL = "$BASE_URL/pages/shipping-ex"
    const val REFUND_URL = "$BASE_URL/policies/refund-policy"
    const val PRIVACY_URL = "$BASE_URL/pages/privacy-pol"
    const val TERMS_URL = "$BASE_URL/policies/terms-of-service"

    /** Sister site: free gemstone recommendation by birth chart. */
    const val RASHI_URL = "https://rashisutra.com/"

    /** Largest quantity of one item a customer can put in the cart. */
    const val MAX_QUANTITY = 10
}
