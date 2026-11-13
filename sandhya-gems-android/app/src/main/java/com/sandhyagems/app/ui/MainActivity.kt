package com.sandhyagems.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.sandhyagems.app.R
import com.sandhyagems.app.data.Shopify

/** The four bottom tabs. Each tab is a [Screen] created the first time it is opened. */
class MainActivity : BaseActivity() {
    lateinit var contentFrame: FrameLayout
        private set

    private val screens = arrayOfNulls<Screen>(TAB_COUNT)
    private lateinit var navItems: List<View>
    private var current = -1
    private val cartListener: () -> Unit = {
        updateBadge()
        (screens[TAB_CART] as? CartScreen)?.render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyInsets(findViewById(R.id.root))
        contentFrame = findViewById(R.id.content)

        navItems = listOf(R.id.nav_home, R.id.nav_categories, R.id.nav_cart, R.id.nav_more).map { findViewById(it) }
        val icons = intArrayOf(R.drawable.ic_home, R.drawable.ic_grid, R.drawable.ic_cart, R.drawable.ic_menu)
        val labels = intArrayOf(R.string.tab_home, R.string.tab_categories, R.string.tab_cart, R.string.tab_more)
        navItems.forEachIndexed { index, item ->
            item.findViewById<ImageView>(R.id.nav_icon).setImageResource(icons[index])
            item.findViewById<TextView>(R.id.nav_label).setText(labels[index])
            item.contentDescription = getString(labels[index])
            item.setOnClickListener { select(index) }
        }

        select(savedInstanceState?.getInt(STATE_TAB) ?: TAB_HOME)
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        app.cart.addListener(cartListener)
        updateBadge()
        screens[current]?.onShown()
    }

    override fun onStop() {
        app.cart.removeListener(cartListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, current)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (current != TAB_HOME) select(TAB_HOME) else super.onBackPressed()
    }

    fun select(tab: Int) {
        if (tab == current) return
        val screen = screens[tab] ?: createScreen(tab).also {
            screens[tab] = it
            contentFrame.addView(it.view)
        }
        screens.forEachIndexed { index, s -> s?.view?.show(index == tab) }
        navItems.forEachIndexed { index, item -> setSelectedDeep(item, index == tab) }
        current = tab
        screen.onShown()
    }

    private fun createScreen(tab: Int): Screen = when (tab) {
        TAB_CATEGORIES -> CategoriesScreen(this)
        TAB_CART -> CartScreen(this)
        TAB_MORE -> MoreScreen(this)
        else -> HomeScreen(this)
    }

    private fun setSelectedDeep(view: View, selected: Boolean) {
        view.isSelected = selected
        view.findViewById<View>(R.id.nav_icon).isSelected = selected
        view.findViewById<View>(R.id.nav_label).isSelected = selected
    }

    private fun updateBadge() {
        val badge = navItems[TAB_CART].findViewById<TextView>(R.id.nav_badge)
        val count = app.cart.count
        badge.show(count > 0)
        badge.text = if (count > 99) "99+" else count.toString()
    }

    /** Handles "open the cart" requests from other screens and product links from the website. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.hasExtra(EXTRA_TAB)) {
            select(intent.getIntExtra(EXTRA_TAB, TAB_HOME))
            return
        }
        val uri = intent.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "https" || !Shopify.isStoreHost(uri.host)) return
        val segments = uri.pathSegments
        val productAt = segments.indexOf("products")
        when {
            // Also matches /collections/{collection}/products/{handle}.
            productAt >= 0 && productAt + 1 < segments.size ->
                ProductDetailActivity.start(this, segments[productAt + 1])
            segments.size >= 2 && segments[0] == "collections" ->
                if (segments[1] == "all") ProductListActivity.all(this)
                else ProductListActivity.collection(this, segments[1], null)
        }
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_CATEGORIES = 1
        const val TAB_CART = 2
        const val TAB_MORE = 3
        private const val TAB_COUNT = 4
        private const val EXTRA_TAB = "tab"
        private const val STATE_TAB = "tab"

        /** Returns to the main screen on the given tab, closing whatever is on top of it. */
        fun open(context: Context, tab: Int) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_TAB, tab)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}

/** One bottom tab. */
abstract class Screen(protected val activity: MainActivity, layout: Int) {
    val view: View = activity.layoutInflater.inflate(layout, activity.contentFrame, false)

    /** Called each time the tab becomes visible. */
    open fun onShown() {}
}
