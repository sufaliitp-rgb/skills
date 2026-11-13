package com.sandhyagems.app.data

import com.sandhyagems.app.util.Money
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Parses the public storefront JSON that every Shopify store serves. */
object ShopifyParser {

    /** Parses `{"products":[...]}` as served by /products.json and /collections/{handle}/products.json. */
    fun parseProducts(json: String): List<Product> {
        val array = JSONObject(json).optJSONArray("products") ?: return emptyList()
        val out = ArrayList<Product>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            parseProduct(item)?.let(out::add)
        }
        return out
    }

    private fun parseProduct(o: JSONObject): Product? {
        val handle = o.optString("handle")
        val variants = parseVariants(o.optJSONArray("variants"))
        // A product with no handle or nothing to buy cannot be shown or sold.
        if (handle.isEmpty() || variants.isEmpty()) return null
        return Product(
            id = o.optLong("id"),
            title = o.optString("title").trim(),
            handle = handle,
            descriptionHtml = text(o, "body_html"),
            vendor = text(o, "vendor"),
            productType = text(o, "product_type"),
            tags = parseTags(o.opt("tags")),
            images = parseImages(o.optJSONArray("images")),
            variants = variants,
            publishedAt = text(o, "published_at"),
        )
    }

    private fun parseVariants(array: JSONArray?): List<Variant> {
        if (array == null) return emptyList()
        val out = ArrayList<Variant>(array.length())
        for (i in 0 until array.length()) {
            val v = array.optJSONObject(i) ?: continue
            val id = v.optLong("id")
            val price = Money.parse(text(v, "price")) ?: continue
            if (id == 0L) continue
            out.add(
                Variant(
                    id = id,
                    title = text(v, "title"),
                    price = price,
                    compareAtPrice = Money.parse(text(v, "compare_at_price"))?.takeIf { it > BigDecimal.ZERO },
                    available = v.optBoolean("available", true),
                )
            )
        }
        return out
    }

    private fun parseImages(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            // Usually objects with a "src"; some endpoints give plain URL strings.
            val src = when (val item = array.opt(i)) {
                is JSONObject -> text(item, "src")
                is String -> item
                else -> ""
            }
            if (src.isNotBlank()) out.add(src)
        }
        return out
    }

    /** Tags arrive as an array on the storefront and as a comma-separated string elsewhere. */
    private fun parseTags(raw: Any?): List<String> = when (raw) {
        is JSONArray -> (0 until raw.length()).map { raw.optString(it).trim() }.filter { it.isNotEmpty() }
        is String -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    /** Parses `{"collections":[...]}` as served by /collections.json. */
    fun parseCollections(json: String): List<ShopCollection> {
        val array = JSONObject(json).optJSONArray("collections") ?: return emptyList()
        val out = ArrayList<ShopCollection>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val handle = o.optString("handle")
            if (handle.isEmpty()) continue
            out.add(
                ShopCollection(
                    id = o.optLong("id"),
                    title = o.optString("title").trim(),
                    handle = handle,
                    image = o.optJSONObject("image")?.let { text(it, "src") }?.takeIf { it.isNotBlank() },
                    productsCount = o.optInt("products_count", -1),
                )
            )
        }
        return out
    }

    /** Like optString, but JSON null becomes "" instead of the word "null". */
    private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key)
}
