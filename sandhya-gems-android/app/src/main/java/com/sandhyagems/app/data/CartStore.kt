package com.sandhyagems.app.data

import android.content.SharedPreferences
import com.sandhyagems.app.StoreConfig
import com.sandhyagems.app.util.Money
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** The shopping cart, saved on the phone so it survives closing the app. */
class CartStore(private val prefs: SharedPreferences) {
    private val listeners = ArrayList<() -> Unit>()

    var lines: List<CartLine> = read()
        private set

    val count: Int get() = lines.sumOf { it.quantity }
    val subtotal: BigDecimal get() = lines.filter { it.available }.fold(BigDecimal.ZERO) { sum, l -> sum + l.total }
    val hasUnavailable: Boolean get() = lines.any { !it.available }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun quantityOf(variantId: Long): Int = lines.firstOrNull { it.variantId == variantId }?.quantity ?: 0

    fun add(product: Product, variant: Variant, quantity: Int = 1) {
        val existing = lines.firstOrNull { it.variantId == variant.id }
        val newQuantity = ((existing?.quantity ?: 0) + quantity).coerceIn(1, StoreConfig.MAX_QUANTITY)
        val line = CartLine(
            variantId = variant.id,
            handle = product.handle,
            title = product.title,
            variantTitle = if (variant.isDefault) "" else variant.title,
            price = variant.price,
            image = product.firstImage,
            quantity = newQuantity,
            available = variant.available,
        )
        save(if (existing == null) lines + line else lines.map { if (it.variantId == variant.id) line else it })
    }

    /** Sets a new quantity; zero or less removes the line. */
    fun setQuantity(variantId: Long, quantity: Int) {
        if (quantity <= 0) {
            remove(variantId)
            return
        }
        val capped = quantity.coerceAtMost(StoreConfig.MAX_QUANTITY)
        save(lines.map { if (it.variantId == variantId) it.copy(quantity = capped) else it })
    }

    fun remove(variantId: Long) = save(lines.filter { it.variantId != variantId })

    fun clear() = save(emptyList())

    /**
     * Brings saved lines up to date with the latest catalogue: a price that
     * changed in the store, or a stone that has since sold. A line whose
     * product is no longer listed is marked unavailable.
     */
    fun syncWith(products: List<Product>) {
        if (lines.isEmpty()) return
        val variants = HashMap<Long, Variant>()
        products.forEach { p -> p.variants.forEach { variants[it.id] = it } }
        val updated = lines.map { line ->
            val v = variants[line.variantId]
            if (v == null) line.copy(available = false) else line.copy(price = v.price, available = v.available)
        }
        if (updated != lines) save(updated)
    }

    /** Checkout link for everything that can still be bought. */
    fun checkoutUrl(): String = Shopify.checkoutUrl(lines.filter { it.available }.map { it.variantId to it.quantity })

    private fun save(newLines: List<CartLine>) {
        lines = newLines
        val array = JSONArray()
        newLines.forEach { l ->
            array.put(
                JSONObject()
                    .put("variantId", l.variantId)
                    .put("handle", l.handle)
                    .put("title", l.title)
                    .put("variantTitle", l.variantTitle)
                    .put("price", l.price.toPlainString())
                    .put("image", l.image ?: "")
                    .put("quantity", l.quantity)
                    .put("available", l.available)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
        ArrayList(listeners).forEach { it() }
    }

    private fun read(): List<CartLine> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val price = Money.parse(o.optString("price")) ?: return@mapNotNull null
            CartLine(
                variantId = o.getLong("variantId"),
                handle = o.optString("handle"),
                title = o.optString("title"),
                variantTitle = o.optString("variantTitle"),
                price = price,
                image = o.optString("image").takeIf { it.isNotEmpty() },
                quantity = o.optInt("quantity", 1).coerceIn(1, StoreConfig.MAX_QUANTITY),
                available = o.optBoolean("available", true),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val KEY = "lines"
    }
}

/** Products the customer has saved for later, by handle. */
class Wishlist(private val prefs: SharedPreferences) {
    var handles: Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
        private set

    fun contains(handle: String): Boolean = handle in handles

    /** Adds or removes the product; returns true if it is now saved. */
    fun toggle(handle: String): Boolean {
        val saved = handle !in handles
        handles = if (saved) handles + handle else handles - handle
        prefs.edit().putStringSet(KEY, handles).apply()
        return saved
    }

    private companion object {
        const val KEY = "handles"
    }
}
