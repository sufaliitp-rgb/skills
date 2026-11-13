package com.sandhyagems.app.data

import com.sandhyagems.app.StoreConfig
import java.math.BigDecimal

data class Variant(
    val id: Long,
    val title: String,
    val price: BigDecimal,
    val compareAtPrice: BigDecimal?,
    val available: Boolean,
) {
    /** Shopify names the only variant of a simple product "Default Title". */
    val isDefault: Boolean get() = title.equals("Default Title", ignoreCase = true) || title.isBlank()
}

data class Product(
    val id: Long,
    val title: String,
    val handle: String,
    val descriptionHtml: String,
    val vendor: String,
    val productType: String,
    val tags: List<String>,
    val images: List<String>,
    val variants: List<Variant>,
    val publishedAt: String,
) {
    val firstImage: String? get() = images.firstOrNull()
    val available: Boolean get() = variants.any { it.available }

    /** The variant shown first: the cheapest one in stock, else the cheapest. */
    val defaultVariant: Variant
        get() = variants.filter { it.available }.minByOrNull { it.price } ?: variants.minBy { it.price }

    val hasPriceRange: Boolean get() = variants.map { it.price }.distinct().size > 1
    val url: String get() = "${StoreConfig.BASE_URL}/products/$handle"

    val searchText: String by lazy {
        (title + " " + productType + " " + vendor + " " + tags.joinToString(" ")).lowercase()
    }
}

data class ShopCollection(
    val id: Long,
    val title: String,
    val handle: String,
    val image: String?,
    /** -1 when the store did not report a count. */
    val productsCount: Int,
)

data class CartLine(
    val variantId: Long,
    val handle: String,
    val title: String,
    val variantTitle: String,
    val price: BigDecimal,
    val image: String?,
    val quantity: Int,
    val available: Boolean = true,
) {
    val total: BigDecimal get() = price.multiply(BigDecimal(quantity))
}
