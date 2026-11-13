package com.sandhyagems.app.data

import android.content.Context
import com.sandhyagems.app.StoreConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** The store's products and collections, loaded from its public Shopify JSON. */
object Catalog {
    private const val PAGE_SIZE = 250
    private const val MAX_PAGES = 20
    private const val MINUTE = 60_000L

    private lateinit var dir: File
    private val perCollection = HashMap<String, CachedResource<List<Product>>>()
    private val byHandle = ConcurrentHashMap<String, Product>()

    /** Every product in the store. */
    lateinit var products: CachedResource<List<Product>>
        private set

    /** Every collection that has at least one product. */
    lateinit var collections: CachedResource<List<ShopCollection>>
        private set

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.cacheDir, "catalog").apply { mkdirs() }
        products = CachedResource(
            file = File(dir, "products.json"),
            ttlMs = 15 * MINUTE,
            fetch = { fetchAllPages("/products.json") },
            parse = ::parseAndIndex,
        )
        collections = CachedResource(
            file = File(dir, "collections.json"),
            ttlMs = 60 * MINUTE,
            fetch = { Http.get("${StoreConfig.BASE_URL}/collections.json?limit=$PAGE_SIZE") },
            parse = { json -> ShopifyParser.parseCollections(json).filter { it.productsCount != 0 } },
        )
    }

    /** The products of one collection, in the order set in the store admin. */
    fun collection(handle: String): CachedResource<List<Product>> = perCollection.getOrPut(handle) {
        CachedResource(
            file = File(dir, "collection_" + handle.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".json"),
            ttlMs = 15 * MINUTE,
            fetch = { fetchAllPages("/collections/${URLEncoder.encode(handle, "UTF-8")}/products.json") },
            parse = ::parseAndIndex,
        )
    }

    /** Looks up a product already seen in any loaded list. */
    fun find(handle: String): Product? = byHandle[handle]

    /**
     * Search runs on the phone over the already-loaded catalogue, so results
     * appear as the customer types and work offline. Every word must match.
     */
    fun search(all: List<Product>, query: String): List<Product> {
        val q = query.trim().lowercase()
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return all
        return all.filter { p -> tokens.all { p.searchText.contains(it) } }
            .sortedByDescending { p ->
                val title = p.title.lowercase()
                when {
                    title.startsWith(q) -> 3
                    tokens.all { title.contains(it) } -> 2
                    else -> 1
                }
            }
    }

    private fun parseAndIndex(json: String): List<Product> {
        val list = ShopifyParser.parseProducts(json)
        list.forEach { byHandle[it.handle] = it }
        return list
    }

    /** Shopify returns at most 250 products per request, so read page after page and join them. */
    private fun fetchAllPages(path: String): String {
        val all = JSONArray()
        for (page in 1..MAX_PAGES) {
            val body = Http.get("${StoreConfig.BASE_URL}$path?limit=$PAGE_SIZE&page=$page")
            val items = JSONObject(body).optJSONArray("products") ?: break
            for (i in 0 until items.length()) all.put(items.get(i))
            if (items.length() < PAGE_SIZE) break
        }
        return JSONObject().put("products", all).toString()
    }
}
