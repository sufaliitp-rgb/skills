package com.sandhyagems.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.sandhyagems.app.data.Catalog
import com.sandhyagems.app.data.ShopifyParser
import com.sandhyagems.app.ui.MainActivity
import com.sandhyagems.app.ui.ProductDetailActivity
import com.sandhyagems.app.ui.ProductListActivity
import com.sandhyagems.app.ui.SearchActivity
import com.sandhyagems.app.ui.WebActivity
import com.sandhyagems.app.util.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the real screens with sample store data (no network) and saves a
 * picture of each one to app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xhdpi")
class UiTest {
    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seedStore() {
        ImageLoader.override = ::gemPicture
        val products = sampleProducts()
        Catalog.products.prime(products)
        Catalog.collections.prime(
            ShopifyParser.parseCollections(fixture("collections.json")).filter { it.productsCount != 0 }
        )
        Catalog.collection(StoreConfig.NEW_ARRIVALS_HANDLE).prime(products.sortedByDescending { it.publishedAt }.take(6))
        Catalog.collection(StoreConfig.BEST_SELLERS_HANDLE).prime(products.filter { it.variants[0].compareAtPrice != null })
        Catalog.collection("ruby").prime(products.filter { it.productType == "Ruby" })
        Catalog.collection("empty").prime(emptyList())
    }

    // ------------------------------------------------------------ helpers

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private inline fun <reified T : Activity> launch(intent: Intent? = null): T {
        val controller = if (intent == null) Robolectric.buildActivity(T::class.java)
        else Robolectric.buildActivity(T::class.java, intent)
        val activity = controller.setup().get()
        idle()
        return activity
    }

    private fun productIntent(handle: String) =
        Intent(app, ProductDetailActivity::class.java).putExtra("handle", handle)

    private fun nextStarted(activity: Activity): Intent? = shadowOf(activity).nextStartedActivity

    private fun text(activity: Activity, id: Int): String = activity.findViewById<TextView>(id).text.toString()

    private fun isShown(activity: Activity, id: Int): Boolean = activity.findViewById<View>(id).isShown

    /** The tab currently on screen. Tabs reuse view ids, so look inside the visible one. */
    private fun currentTab(main: MainActivity): View {
        val frame = main.contentFrame
        return (0 until frame.childCount).map { frame.getChildAt(it) }.single { it.visibility == View.VISIBLE }
    }

    private fun tabText(main: MainActivity, id: Int): String = currentTab(main).findViewById<TextView>(id).text.toString()

    private fun snap(activity: Activity, name: String) {
        idle()
        val decor = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        // Render the way a phone does (rounded photos, shadows); fall back to plain drawing.
        var result = -1
        try {
            PixelCopy.request(activity.window, bitmap, { result = it }, Handler(Looper.getMainLooper()))
            idle()
        } catch (e: Throwable) {
            result = -1
        }
        if (result != PixelCopy.SUCCESS) decor.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Stands in for the network: answers each picture request with one of
     * Sandhya Gems' own photographs, chosen by the stone named in the address.
     */
    private fun gemPicture(url: String): Bitmap? {
        val name = listOf("blue-zircon" to "blue_zircon", "zircon" to "zircon", "ruby" to "ruby", "emerald" to "emerald",
            "citrine" to "citrine", "coral" to "coral", "gomed" to "gomed", "opal" to "opal", "pearl" to "pearl")
            .firstOrNull { it.first in url }?.second ?: return null
        return photos.getOrPut(name) {
            javaClass.classLoader!!.getResourceAsStream("photos/$name.jpg")!!.use(BitmapFactory::decodeStream)
        }
    }

    private val photos = HashMap<String, Bitmap>()

    // ------------------------------------------------------------ home and tabs

    @Test
    fun homeShowsCategoriesAndProductRows() {
        val main = launch<MainActivity>()
        assertTrue(isShown(main, R.id.home_categories_section))
        assertTrue(isShown(main, R.id.home_new))
        assertTrue(isShown(main, R.id.home_best))
        assertTrue(isShown(main, R.id.home_all))
        assertFalse(isShown(main, R.id.home_state))
        assertFalse(isShown(main, R.id.home_consult)) // this sample store sells no consultation
        // The collection with no products is not offered as a category.
        assertEquals(6, main.findViewById<RecyclerView>(R.id.home_categories).adapter!!.itemCount)
        snap(main, "01-home")

        main.findViewById<View>(R.id.home_scroll).scrollTo(0, 100_000)
        snap(main, "02-home-bottom")
    }

    @Test
    fun consultationIsOfferedWhenTheStoreSellsOne() {
        val ruby = sampleProducts().first()
        val consultation = ruby.copy(
            id = 99, title = "Personal Astrology Consultation (Online)", handle = "astrology-consultation",
            variants = listOf(ruby.defaultVariant.copy(id = 990, price = java.math.BigDecimal("499.00"), compareAtPrice = null)),
        )
        Catalog.products.prime(sampleProducts() + consultation)

        val main = launch<MainActivity>()
        assertTrue(main.findViewById<View>(R.id.home_consult).visibility == View.VISIBLE)
        assertEquals("Personal consultation online, ₹499", text(main, R.id.home_consult_text))
        main.findViewById<View>(R.id.home_consult).performClick()
        assertEquals("astrology-consultation", nextStarted(main)!!.getStringExtra("handle"))
    }

    @Test
    fun categoryWithoutAStorePictureGetsOurOwnPhoto() {
        assertEquals(R.drawable.gem_opal, com.sandhyagems.app.ui.CategoryArt.forTitle("Opal"))
        assertEquals(R.drawable.gem_ruby, com.sandhyagems.app.ui.CategoryArt.forTitle("Natural Ruby (Manik)"))
        assertEquals(R.drawable.gem_blue_zircon, com.sandhyagems.app.ui.CategoryArt.forTitle("Blue Zircon"))
        assertEquals(R.drawable.gem_zircon, com.sandhyagems.app.ui.CategoryArt.forTitle("White Zircon"))
        assertNull(com.sandhyagems.app.ui.CategoryArt.forTitle("Best sellers"))
    }

    @Test
    fun directionsOpenTheShopInMaps() {
        val main = launch<MainActivity>()
        main.findViewById<View>(R.id.nav_more).performClick()
        idle()
        val container = currentTab(main).findViewById<android.view.ViewGroup>(R.id.more_container)
        val row = (0 until container.childCount).map { container.getChildAt(it) }
            .first { it.findViewById<TextView>(R.id.row_title)?.text?.toString() == "Get directions" }
        row.performClick()
        val started = nextStarted(main)!!
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertTrue(started.dataString!!.startsWith("geo:22.6983,88.4371?q=Sandhya%20Gems%20Corner"))
    }

    @Test
    @Config(qualifiers = "+night")
    fun homeInDarkMode() {
        snap(launch<MainActivity>(), "03-home-dark")
    }

    @Test
    fun tabsSwitchAndBackReturnsHome() {
        val main = launch<MainActivity>()
        main.findViewById<View>(R.id.nav_categories).performClick()
        idle()
        assertFalse(isShown(main, R.id.home_refresh))
        snap(main, "04-categories")

        main.findViewById<View>(R.id.nav_more).performClick()
        snap(main, "05-more")

        @Suppress("DEPRECATION")
        main.onBackPressed()
        assertTrue(isShown(main, R.id.home_refresh))
        assertFalse(main.isFinishing)
    }

    @Test
    fun emptyCartInvitesShopping() {
        val main = launch<MainActivity>()
        main.findViewById<View>(R.id.nav_cart).performClick()
        idle()
        assertEquals("Your cart is empty", tabText(main, R.id.state_title))
        assertFalse(currentTab(main).findViewById<View>(R.id.cart_summary).isShown)
        snap(main, "06-cart-empty")
    }

    @Test
    fun cartShowsLinesBadgeAndChecksOut() {
        val products = sampleProducts()
        val ruby = products.first { it.handle == "natural-ruby-gemstone" }
        val emerald = products.first { it.handle == "natural-emerald-panna" }
        app.cart.add(ruby, ruby.defaultVariant)
        app.cart.add(emerald, emerald.variants[1], 2)

        val main = launch<MainActivity>()
        val cartTab = main.findViewById<View>(R.id.nav_cart)
        assertEquals("3", cartTab.findViewById<TextView>(R.id.nav_badge).text.toString())
        cartTab.performClick()
        idle()
        assertEquals("₹20,900", tabText(main, R.id.cart_subtotal))
        snap(main, "07-cart")

        currentTab(main).findViewById<View>(R.id.cart_checkout).performClick()
        val started = nextStarted(main)!!
        assertEquals(WebActivity::class.java.name, started.component!!.className)
        assertEquals("https://sandhyagems.in/cart/101:1,104:2", started.getStringExtra("url"))
        assertTrue(started.getBooleanExtra("clear_cart", false))
    }

    @Test
    fun cartQuantityButtonsUpdateTotals() {
        val ruby = sampleProducts().first()
        app.cart.add(ruby, ruby.defaultVariant)
        val main = launch<MainActivity>()
        main.findViewById<View>(R.id.nav_cart).performClick()
        idle()

        currentTab(main).findViewById<View>(R.id.line_plus).performClick()
        idle()
        assertEquals("₹17,000", tabText(main, R.id.cart_subtotal))
        assertEquals("2", tabText(main, R.id.line_quantity))

        currentTab(main).findViewById<View>(R.id.line_remove).performClick()
        idle()
        assertTrue(app.cart.lines.isEmpty())
        assertEquals("Your cart is empty", tabText(main, R.id.state_title))
    }

    // ------------------------------------------------------------ lists and search

    @Test
    fun collectionListSortsByPrice() {
        val list = launch<ProductListActivity>(
            Intent(app, ProductListActivity::class.java).putExtra("mode", 0)
        )
        assertEquals("All gemstones", text(list, R.id.toolbar_title))
        assertEquals("10 items", text(list, R.id.list_count))
        snap(list, "08-all-gemstones")

        val ruby = launch<ProductListActivity>(
            Intent(app, ProductListActivity::class.java).putExtra("mode", 1).putExtra("handle", "ruby").putExtra("title", "Ruby")
        )
        assertEquals("2 items", text(ruby, R.id.list_count))
    }

    @Test
    fun emptyCollectionAndEmptyWishlistExplainThemselves() {
        val empty = launch<ProductListActivity>(
            Intent(app, ProductListActivity::class.java).putExtra("mode", 1).putExtra("handle", "empty")
        )
        assertEquals("Empty", text(empty, R.id.toolbar_title))
        assertEquals("Nothing here yet", text(empty, R.id.state_title))
        assertFalse(isShown(empty, R.id.list_bar))

        val wishlist = launch<ProductListActivity>(
            Intent(app, ProductListActivity::class.java).putExtra("mode", 2)
        )
        assertEquals("No saved stones yet", text(wishlist, R.id.state_title))
    }

    @Test
    fun searchFiltersAsYouType() {
        val search = launch<SearchActivity>()
        val list = search.findViewById<RecyclerView>(R.id.list)
        assertEquals(10, list.adapter!!.itemCount)

        search.findViewById<TextView>(R.id.search_input).text = "zircon"
        idle()
        assertEquals(2, list.adapter!!.itemCount)
        snap(search, "09-search")

        search.findViewById<TextView>(R.id.search_input).text = "diamond"
        idle()
        assertEquals(0, list.adapter!!.itemCount)
        assertEquals("No stones found", text(search, R.id.state_title))
    }

    // ------------------------------------------------------------ product page

    @Test
    fun productPageAddsToCartThenOffersTheCart() {
        val page = launch<ProductDetailActivity>(productIntent("natural-ruby-gemstone"))
        assertEquals("Certified Natural Ruby Gemstone for Astrology", text(page, R.id.product_title))
        assertEquals("₹8,500", text(page, R.id.product_price))
        assertEquals("₹10,000", text(page, R.id.product_compare))
        assertEquals("15% off", text(page, R.id.product_discount))
        assertEquals("In stock", text(page, R.id.product_stock))
        assertEquals("1 / 3", text(page, R.id.product_gallery_count))
        assertFalse(isShown(page, R.id.product_options_scroll))
        assertTrue(text(page, R.id.product_description).contains("Natural, untreated stone"))
        snap(page, "10-product")

        page.findViewById<View>(R.id.product_add).performClick()
        assertEquals(1, app.cart.count)
        assertEquals("Go to cart", text(page, R.id.product_add))

        page.findViewById<View>(R.id.product_add).performClick()
        assertEquals(1, app.cart.count) // a second tap opens the cart instead of adding again
        assertEquals(MainActivity::class.java.name, nextStarted(page)!!.component!!.className)
    }

    @Test
    fun buyNowChecksOutOnlyThatStone() {
        val other = sampleProducts().first { it.handle == "natural-op" }
        app.cart.add(other, other.defaultVariant)

        val page = launch<ProductDetailActivity>(productIntent("ysapph"))
        page.findViewById<View>(R.id.product_buy).performClick()
        val started = nextStarted(page)!!
        assertEquals("https://sandhyagems.in/cart/106:1", started.getStringExtra("url"))
        assertFalse(started.getBooleanExtra("clear_cart", false))
        assertEquals(1, app.cart.count)
    }

    @Test
    fun choosingAVariantChangesPriceAndStock() {
        val page = launch<ProductDetailActivity>(productIntent("natural-emerald-panna"))
        assertTrue(isShown(page, R.id.product_options_scroll))
        assertEquals("₹4,500", text(page, R.id.product_price))
        val options = page.findViewById<android.view.ViewGroup>(R.id.product_options)
        assertEquals(3, options.childCount)
        snap(page, "11-product-options")

        options.getChildAt(1).performClick()
        assertEquals("₹6,200", text(page, R.id.product_price))
        assertTrue(options.getChildAt(1).isSelected)
        assertFalse(options.getChildAt(0).isSelected)

        options.getChildAt(2).performClick()
        assertEquals("Sold out", text(page, R.id.product_stock))
        assertFalse(page.findViewById<View>(R.id.product_buy).isEnabled)
        assertFalse(page.findViewById<View>(R.id.product_add).isEnabled)
    }

    @Test
    fun soldOutStoneCannotBeBought() {
        val page = launch<ProductDetailActivity>(productIntent("cats-eye-k-1"))
        assertEquals("Sold out", text(page, R.id.product_stock))
        assertEquals("Sold out", text(page, R.id.product_add))
        page.findViewById<View>(R.id.product_add).performClick()
        page.findViewById<View>(R.id.product_buy).performClick()
        assertEquals(0, app.cart.count)
        assertNull(nextStarted(page))
    }

    @Test
    fun unknownProductIsExplained() {
        val page = launch<ProductDetailActivity>(productIntent("no-such-stone"))
        assertEquals("This stone is no longer listed", text(page, R.id.state_title))
        assertFalse(isShown(page, R.id.product_actions))
    }

    @Test
    fun wishlistHeartSavesTheStone() {
        val page = launch<ProductDetailActivity>(productIntent("ysapph"))
        page.findViewById<View>(R.id.toolbar_action2).performClick()
        assertTrue(app.wishlist.contains("ysapph"))

        val wishlist = launch<ProductListActivity>(
            Intent(app, ProductListActivity::class.java).putExtra("mode", 2)
        )
        assertEquals("1 item", text(wishlist, R.id.list_count))
    }

    // ------------------------------------------------------------ links from the website

    private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url), app, MainActivity::class.java)

    @Test
    fun websiteProductLinkOpensTheProductPage() {
        val main = launch<MainActivity>(viewIntent("https://sandhyagems.in/collections/ruby/products/natural-ruby-gemstone?variant=101"))
        val started = nextStarted(main)!!
        assertEquals(ProductDetailActivity::class.java.name, started.component!!.className)
        assertEquals("natural-ruby-gemstone", started.getStringExtra("handle"))
    }

    @Test
    fun websiteCollectionLinkOpensTheCollection() {
        val main = launch<MainActivity>(viewIntent("https://www.sandhyagems.in/collections/ruby"))
        val started = nextStarted(main)!!
        assertEquals(ProductListActivity::class.java.name, started.component!!.className)
        assertEquals("ruby", started.getStringExtra("handle"))
    }

    @Test
    fun linksFromOtherSitesAreIgnored() {
        val main = launch<MainActivity>(viewIntent("https://example.com/products/natural-ruby-gemstone"))
        assertNull(nextStarted(main))
    }
}
