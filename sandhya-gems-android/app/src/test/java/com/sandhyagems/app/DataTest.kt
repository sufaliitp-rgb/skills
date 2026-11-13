package com.sandhyagems.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sandhyagems.app.data.CachedResource
import com.sandhyagems.app.data.CartStore
import com.sandhyagems.app.data.Catalog
import com.sandhyagems.app.data.HttpException
import com.sandhyagems.app.data.Outcome
import com.sandhyagems.app.data.Product
import com.sandhyagems.app.data.Shopify
import com.sandhyagems.app.data.ShopifyParser
import com.sandhyagems.app.data.Wishlist
import com.sandhyagems.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.math.BigDecimal
import java.net.UnknownHostException

fun fixture(name: String): String =
    DataTest::class.java.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

fun sampleProducts(): List<Product> = ShopifyParser.parseProducts(fixture("products.json"))

@RunWith(RobolectricTestRunner::class)
class DataTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // ------------------------------------------------------------ parsing

    @Test
    fun parsesProductsAndSkipsBrokenOnes() {
        val products = sampleProducts()
        assertEquals(10, products.size)
        assertFalse(products.any { it.handle == "no-variants" || it.handle.isEmpty() })

        val ruby = products.first { it.handle == "natural-ruby-gemstone" }
        assertEquals("Certified Natural Ruby Gemstone for Astrology", ruby.title)
        assertEquals(3, ruby.images.size)
        assertEquals(BigDecimal("8500.00"), ruby.defaultVariant.price)
        assertEquals(BigDecimal("10000.00"), ruby.defaultVariant.compareAtPrice)
        assertTrue(ruby.defaultVariant.isDefault)
        assertTrue(ruby.available)
        assertEquals(listOf("ruby", "manik", "sun"), ruby.tags)
    }

    @Test
    fun nullComparePriceIsNotTreatedAsText() {
        val burma = sampleProducts().first { it.handle == "certified-old-burma-ruby-manikgemstone" }
        assertNull(burma.defaultVariant.compareAtPrice)
    }

    @Test
    fun variantsAndStock() {
        val products = sampleProducts()
        val emerald = products.first { it.handle == "natural-emerald-panna" }
        assertEquals(3, emerald.variants.size)
        assertTrue(emerald.hasPriceRange)
        assertEquals("3 Carat", emerald.defaultVariant.title)
        assertFalse(emerald.variants.last().available)

        val catsEye = products.first { it.handle == "cats-eye-k-1" }
        assertFalse(catsEye.available)
    }

    @Test
    fun tagsGivenAsCommaTextAreSplit() {
        val blue = sampleProducts().first { it.handle == "blue-sapphire-neelam" }
        assertEquals(listOf("zircon", "blue zircon", "venus"), blue.tags)
    }

    @Test
    fun parsesCollections() {
        val collections = ShopifyParser.parseCollections(fixture("collections.json"))
        assertEquals(7, collections.size) // the one without a handle is dropped
        assertEquals("ruby", collections[0].handle)
        assertEquals(2, collections[0].productsCount)
        assertNull(collections.first { it.handle == "opal" }.image)
    }

    @Test
    fun emptyOrUnexpectedJsonGivesEmptyLists() {
        assertTrue(ShopifyParser.parseProducts("{}").isEmpty())
        assertTrue(ShopifyParser.parseCollections("{\"collections\":[]}").isEmpty())
    }

    // ------------------------------------------------------------ money

    @Test
    fun formatsRupeesInIndianGrouping() {
        assertEquals("₹950", Money.format(BigDecimal("950.00")))
        assertEquals("₹8,500", Money.format(BigDecimal("8500.00")))
        assertEquals("₹1,25,000", Money.format(BigDecimal("125000")))
        assertEquals("₹12,34,56,789", Money.format(BigDecimal("123456789")))
        assertEquals("₹3,200.50", Money.format(BigDecimal("3200.5")))
        assertEquals("₹0", Money.format(BigDecimal.ZERO))
    }

    @Test
    fun parsesPrices() {
        assertEquals(BigDecimal("1500.00"), Money.parse("1500.00"))
        assertEquals(BigDecimal("1500"), Money.parse("1,500"))
        assertNull(Money.parse(""))
        assertNull(Money.parse(null))
        assertNull(Money.parse("abc"))
    }

    @Test
    fun discountPercent() {
        assertEquals(15, Money.discountPercent(BigDecimal("8500"), BigDecimal("10000")))
        assertNull(Money.discountPercent(BigDecimal("8500"), null))
        assertNull(Money.discountPercent(BigDecimal("8500"), BigDecimal("8500")))
        assertNull(Money.discountPercent(BigDecimal("8500"), BigDecimal("8000")))
    }

    // ------------------------------------------------------------ shopify urls

    @Test
    fun sizedImageRoundsWidthUpAndKeepsOtherParameters() {
        assertEquals(
            "https://cdn.shopify.com/s/files/a.jpg?v=1&width=320",
            Shopify.sizedImage("https://cdn.shopify.com/s/files/a.jpg?v=1", 300),
        )
        assertEquals(
            "https://cdn.shopify.com/s/files/a.jpg?width=1440",
            Shopify.sizedImage("//cdn.shopify.com/s/files/a.jpg", 5000),
        )
        assertEquals(
            "https://sandhyagems.in/cdn/shop/files/a.jpg?v=2&width=160",
            Shopify.sizedImage("https://sandhyagems.in/cdn/shop/files/a.jpg?width=900&v=2", 100),
        )
        assertEquals("https://example.com/a.jpg", Shopify.sizedImage("https://example.com/a.jpg", 100))
        assertNull(Shopify.sizedImage("", 100))
        assertNull(Shopify.sizedImage(null, 100))
    }

    @Test
    fun checkoutUrlIsACartPermalink() {
        assertEquals("https://sandhyagems.in/cart/101:1,104:2", Shopify.checkoutUrl(listOf(101L to 1, 104L to 2)))
    }

    @Test
    fun recognisesOrderConfirmationPages() {
        assertTrue(Shopify.isOrderComplete("https://sandhyagems.in/checkouts/cn/abc123/thank-you"))
        assertTrue(Shopify.isOrderComplete("https://sandhyagems.in/12345/checkouts/abc/thank_you?x=1"))
        assertTrue(Shopify.isOrderComplete("https://sandhyagems.in/12345/orders/0123456789abcdef0123456789abcdef"))
        assertFalse(Shopify.isOrderComplete("https://sandhyagems.in/checkouts/cn/abc123/information"))
        assertFalse(Shopify.isOrderComplete("https://sandhyagems.in/cart/101:1"))
        assertFalse(Shopify.isOrderComplete("https://sandhyagems.in/account/orders"))
        assertFalse(Shopify.isOrderComplete("https://sandhyagems.in/account/orders/0123456789abcdef0123456789abcdef"))
        assertFalse(Shopify.isOrderComplete(null))
    }

    // ------------------------------------------------------------ search

    @Test
    fun searchMatchesTitleTypeAndTags() {
        val all = sampleProducts()
        assertEquals(all, Catalog.search(all, "  "))
        assertEquals(
            setOf("natural-ruby-gemstone", "certified-old-burma-ruby-manikgemstone"),
            Catalog.search(all, "ruby").map { it.handle }.toSet(),
        )
        assertEquals(listOf("natural-emerald-panna"), Catalog.search(all, "PANNA").map { it.handle })
        assertEquals(1, Catalog.search(all, "coral").size)
        assertEquals(2, Catalog.search(all, "zircon").size)
        assertEquals(listOf("red-coral-moonga"), Catalog.search(all, "red coral").map { it.handle })
        assertEquals(listOf("ysapph"), Catalog.search(all, "jupiter").map { it.handle })
        assertTrue(Catalog.search(all, "diamond").isEmpty())
    }

    @Test
    fun searchPutsTitlesStartingWithTheQueryFirst() {
        val all = sampleProducts()
        assertEquals("natural-op", Catalog.search(all, "opal").first().handle)
        assertEquals("blue-sapphire-neelam", Catalog.search(all, "blue").first().handle)
    }

    // ------------------------------------------------------------ cart

    private fun newCart() = CartStore(context.getSharedPreferences("cart-test", Context.MODE_PRIVATE))

    @Test
    fun cartAddsMergesAndTotals() {
        val products = sampleProducts()
        val ruby = products.first { it.handle == "natural-ruby-gemstone" }
        val emerald = products.first { it.handle == "natural-emerald-panna" }
        val cart = newCart()
        var notified = 0
        cart.addListener { notified++ }

        cart.add(ruby, ruby.defaultVariant)
        cart.add(ruby, ruby.defaultVariant)
        cart.add(emerald, emerald.variants[1])

        assertEquals(2, cart.lines.size)
        assertEquals(3, cart.count)
        assertEquals(3, notified)
        assertEquals(BigDecimal("23200.00"), cart.subtotal)
        assertEquals("", cart.lines[0].variantTitle)
        assertEquals("4 Carat", cart.lines[1].variantTitle)
        assertEquals("https://sandhyagems.in/cart/101:2,104:1", cart.checkoutUrl())
    }

    @Test
    fun cartSurvivesRestart() {
        val ruby = sampleProducts().first()
        newCart().add(ruby, ruby.defaultVariant, 2)
        val reopened = newCart()
        assertEquals(1, reopened.lines.size)
        assertEquals(2, reopened.lines[0].quantity)
        assertEquals(BigDecimal("8500.00"), reopened.lines[0].price)
    }

    @Test
    fun cartQuantityRules() {
        val ruby = sampleProducts().first()
        val cart = newCart()
        cart.add(ruby, ruby.defaultVariant)
        cart.setQuantity(101, 50)
        assertEquals(StoreConfig.MAX_QUANTITY, cart.quantityOf(101))
        cart.setQuantity(101, 0)
        assertTrue(cart.lines.isEmpty())
        assertEquals(0, cart.quantityOf(101))
    }

    @Test
    fun cartPicksUpNewPricesAndSoldOutStones() {
        val products = sampleProducts()
        val ruby = products.first { it.handle == "natural-ruby-gemstone" }
        val opal = products.first { it.handle == "natural-op" }
        val cart = newCart()
        cart.add(ruby, ruby.defaultVariant)
        cart.add(opal, opal.defaultVariant)

        // The ruby's price rises; the opal is no longer listed at all.
        val repriced = ruby.copy(variants = listOf(ruby.defaultVariant.copy(price = BigDecimal("9000.00"))))
        cart.syncWith(listOf(repriced))

        assertEquals(BigDecimal("9000.00"), cart.lines[0].price)
        assertFalse(cart.lines[1].available)
        assertTrue(cart.hasUnavailable)
        assertEquals(BigDecimal("9000.00"), cart.subtotal)
        assertEquals("https://sandhyagems.in/cart/101:1", cart.checkoutUrl())
    }

    @Test
    fun wishlistTogglesAndPersists() {
        val prefs = context.getSharedPreferences("wish-test", Context.MODE_PRIVATE)
        val wishlist = Wishlist(prefs)
        assertTrue(wishlist.toggle("ysapph"))
        assertTrue(wishlist.contains("ysapph"))
        assertTrue(Wishlist(prefs).contains("ysapph"))
        assertFalse(wishlist.toggle("ysapph"))
        assertFalse(Wishlist(prefs).contains("ysapph"))
    }

    // ------------------------------------------------------------ caching

    /** Runs background work and main-thread callbacks until [done] or a timeout. */
    private fun await(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("timed out", done())
    }

    private fun tempFile(name: String) = File(context.cacheDir, name).also { it.delete() }

    @Test
    fun downloadsOnceThenAnswersFromMemory() {
        var fetches = 0
        val resource = CachedResource(tempFile("a.json"), 60_000, { fetches++; "hello" }, { it.uppercase() })
        val seen = ArrayList<Outcome<String>>()
        resource.load { seen.add(it) }
        await { seen.isNotEmpty() }
        assertEquals(Outcome.Success("HELLO", isFinal = true), seen.single())

        resource.load { seen.add(it) }
        assertEquals(2, seen.size)
        assertEquals(1, fetches)
    }

    @Test
    fun showsSavedCopyFirstThenRefreshesWhenStale() {
        val file = tempFile("b.json")
        file.writeText("old")
        file.setLastModified(System.currentTimeMillis() - 120_000)
        val resource = CachedResource(file, 60_000, { "new" }, { it })
        val seen = ArrayList<Outcome<String>>()
        resource.load { seen.add(it) }
        await { seen.size == 2 }
        assertEquals(Outcome.Success("old", isFinal = false), seen[0])
        assertEquals(Outcome.Success("new", isFinal = true), seen[1])
        assertEquals("new", file.readText())
    }

    @Test
    fun freshSavedCopyNeedsNoDownload() {
        val file = tempFile("c.json")
        file.writeText("saved")
        var fetches = 0
        val resource = CachedResource(file, 60_000, { fetches++; "new" }, { it })
        val seen = ArrayList<Outcome<String>>()
        resource.load { seen.add(it) }
        await { seen.isNotEmpty() }
        assertEquals(Outcome.Success("saved", isFinal = true), seen.single())
        assertEquals(0, fetches)

        // And a later load is not left waiting behind a download that never started.
        resource.load(force = true) { seen.add(it) }
        await { seen.size == 3 }
        assertEquals(Outcome.Success("new", isFinal = true), seen[2])
    }

    @Test
    fun offlineWithSavedCopyStillShowsTheCatalogue() {
        val file = tempFile("d.json")
        file.writeText("saved")
        file.setLastModified(System.currentTimeMillis() - 120_000)
        val resource = CachedResource<String>(file, 60_000, { throw UnknownHostException() }, { it })
        val seen = ArrayList<Outcome<String>>()
        resource.load { seen.add(it) }
        await { seen.size == 2 }
        assertEquals(Outcome.Success("saved", isFinal = true, offline = true), seen[1])
    }

    @Test
    fun offlineWithNothingSavedReportsAFailure() {
        val offline = CachedResource<String>(tempFile("e.json"), 60_000, { throw UnknownHostException() }, { it })
        val seen = ArrayList<Outcome<String>>()
        offline.load { seen.add(it) }
        await { seen.isNotEmpty() }
        assertTrue((seen.single() as Outcome.Failure).message.contains("internet"))

        val missing = CachedResource<String>(tempFile("f.json"), 60_000, { throw HttpException(404) }, { it })
        missing.load { seen.add(it) }
        await { seen.size == 2 }
        assertTrue((seen[1] as Outcome.Failure).message.contains("404"))
    }

    @Test
    fun twoScreensAskingAtOnceShareOneDownload() {
        var fetches = 0
        val resource = CachedResource(tempFile("g.json"), 60_000, { fetches++; Thread.sleep(50); "x" }, { it })
        val seen = ArrayList<Outcome<String>>()
        resource.load { seen.add(it) }
        resource.load { seen.add(it) }
        await { seen.size == 2 }
        assertEquals(1, fetches)
    }

    @Test
    fun corruptSavedCopyIsDiscarded() {
        val file = tempFile("h.json")
        file.writeText("not json")
        val resource = CachedResource(file, 60_000, { fixture("products.json") }, ShopifyParser::parseProducts)
        val seen = ArrayList<Outcome<List<Product>>>()
        resource.load { seen.add(it) }
        await { seen.isNotEmpty() }
        assertEquals(10, (seen.single() as Outcome.Success).value.size)
    }
}
