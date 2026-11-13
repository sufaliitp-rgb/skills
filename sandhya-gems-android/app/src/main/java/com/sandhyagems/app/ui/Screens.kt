package com.sandhyagems.app.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sandhyagems.app.BuildConfig
import com.sandhyagems.app.R
import com.sandhyagems.app.StoreConfig
import com.sandhyagems.app.data.CachedResource
import com.sandhyagems.app.data.CartLine
import com.sandhyagems.app.data.Catalog
import com.sandhyagems.app.data.Outcome
import com.sandhyagems.app.data.Product
import com.sandhyagems.app.data.ShopCollection
import com.sandhyagems.app.util.ImageLoader
import com.sandhyagems.app.util.Money

// ---------------------------------------------------------------- Home

class HomeScreen(a: MainActivity) : Screen(a, R.layout.screen_home) {
    private val refresh: SwipeRefreshLayout = view.findViewById(R.id.home_refresh)
    private val state = StateHolder(view.findViewById(R.id.home_state))
    private val categoriesSection: View = view.findViewById(R.id.home_categories_section)
    private val categoryAdapter = CategoryAdapter(circles = true) { openCollection(it) }

    private val newSection = Section(R.id.home_new, R.string.section_new) {
        ProductListActivity.collection(a, StoreConfig.NEW_ARRIVALS_HANDLE, a.getString(R.string.section_new))
    }
    private val bestSection = Section(R.id.home_best, R.string.section_best) {
        ProductListActivity.collection(a, StoreConfig.BEST_SELLERS_HANDLE, a.getString(R.string.section_best))
    }
    private val allSection = Section(R.id.home_all, R.string.section_all) { ProductListActivity.all(a) }
    private var offlineNoticeShown = false

    init {
        view.findViewById<View>(R.id.home_search).setOnClickListener { SearchActivity.start(a) }
        view.findViewById<View>(R.id.home_wishlist).setOnClickListener { ProductListActivity.wishlist(a) }
        view.findViewById<View>(R.id.home_shop_all).setOnClickListener { ProductListActivity.all(a) }
        view.findViewById<View>(R.id.home_rashi).setOnClickListener {
            WebActivity.start(a, StoreConfig.RASHI_URL, a.getString(R.string.more_rashi))
        }
        view.findViewById<View>(R.id.home_whatsapp).setOnClickListener {
            Links.whatsApp(a, a.getString(R.string.whatsapp_general_message))
        }
        view.findViewById<View>(R.id.home_call).setOnClickListener { Links.call(a) }

        // Round off the three stone photos on the banner.
        intArrayOf(R.id.hero_gem_1, R.id.hero_gem_2, R.id.hero_gem_3).forEach {
            view.findViewById<ImageView>(it).apply {
                clipToOutline = true
                cropToPadding = true
            }
        }

        trust(R.id.trust_1, R.drawable.ic_verified, R.string.trust_certified)
        trust(R.id.trust_2, R.drawable.ic_returns, R.string.trust_returns)
        trust(R.id.trust_3, R.drawable.ic_shipping, R.string.trust_shipping)

        view.findViewById<RecyclerView>(R.id.home_categories).apply {
            layoutManager = LinearLayoutManager(a, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        refresh.setColorSchemeColors(a.getColor(R.color.primary))
        refresh.setOnRefreshListener { load(force = true) }
        load(force = false)
    }

    private fun trust(id: Int, icon: Int, label: Int) {
        val item = view.findViewById<View>(id)
        item.findViewById<ImageView>(R.id.trust_icon).setImageResource(icon)
        item.findViewById<TextView>(R.id.trust_label).setText(label)
    }

    private fun openCollection(c: ShopCollection) = ProductListActivity.collection(activity, c.handle, c.title)

    private fun load(force: Boolean) {
        if (!allSection.isVisible) state.loading()

        Catalog.collections.load(force, activity.alive { outcome ->
            if (outcome is Outcome.Success) {
                categoryAdapter.submit(outcome.value)
                categoriesSection.show(outcome.value.isNotEmpty())
            }
        })
        newSection.bind(Catalog.collection(StoreConfig.NEW_ARRIVALS_HANDLE), force)
        bestSection.bind(Catalog.collection(StoreConfig.BEST_SELLERS_HANDLE), force)

        Catalog.products.load(force, activity.alive { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    activity.app.cart.syncWith(outcome.value)
                    showConsultation(outcome.value)
                    allSection.show(outcome.value.take(HOME_ROW_SIZE))
                    if (outcome.value.isEmpty()) {
                        state.message(
                            activity.getString(R.string.empty_collection_title),
                            activity.getString(R.string.empty_collection_text),
                        )
                    } else {
                        state.hide()
                    }
                    if (outcome.isFinal) refresh.isRefreshing = false
                    if (outcome.offline && !offlineNoticeShown) {
                        offlineNoticeShown = true
                        activity.toast(R.string.offline_notice)
                    }
                }
                is Outcome.Failure -> {
                    refresh.isRefreshing = false
                    state.message(
                        activity.getString(R.string.load_failed_title),
                        outcome.message,
                        activity.getString(R.string.try_again),
                    ) { load(force = true) }
                }
            }
        })
    }

    /** Offers the astrology consultation, if the store sells one. */
    private fun showConsultation(products: List<Product>) {
        val consultation = products.firstOrNull {
            it.available && it.title.contains(StoreConfig.CONSULTATION_KEYWORD, ignoreCase = true)
        }
        val card = view.findViewById<View>(R.id.home_consult)
        card.show(consultation != null)
        if (consultation == null) return
        view.findViewById<TextView>(R.id.home_consult_text).text =
            activity.getString(R.string.consult_text, Money.format(consultation.defaultVariant.price))
        card.setOnClickListener { ProductDetailActivity.start(activity, consultation.handle) }
    }

    /** A titled, sideways-scrolling row of products. Hidden until it has something to show. */
    private inner class Section(id: Int, title: Int, onSeeAll: () -> Unit) {
        private val root: View = view.findViewById(id)
        private val adapter = ProductAdapter(cardWidthDp = 150) { ProductDetailActivity.start(activity, it.handle) }
        val isVisible: Boolean get() = root.visibility == View.VISIBLE

        init {
            root.findViewById<TextView>(R.id.section_title).setText(title)
            root.findViewById<View>(R.id.section_see_all).setOnClickListener { onSeeAll() }
            root.findViewById<RecyclerView>(R.id.section_list).apply {
                layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
                adapter = this@Section.adapter
            }
        }

        fun show(products: List<Product>) {
            adapter.submit(products)
            root.show(products.isNotEmpty())
        }

        fun bind(resource: CachedResource<List<Product>>, force: Boolean) {
            resource.load(force, activity.alive { outcome ->
                // A missing collection simply leaves the row hidden.
                if (outcome is Outcome.Success) show(outcome.value.take(HOME_ROW_SIZE))
            })
        }
    }

    private companion object {
        const val HOME_ROW_SIZE = 12
    }
}

// ---------------------------------------------------------------- Categories

class CategoriesScreen(a: MainActivity) : Screen(a, R.layout.screen_list) {
    private val state = StateHolder(view.findViewById(R.id.screen_state))
    private val adapter = CategoryAdapter(circles = false) { ProductListActivity.collection(a, it.handle, it.title) }

    init {
        view.findViewById<TextView>(R.id.screen_title).setText(R.string.tab_categories)
        view.findViewById<View>(R.id.screen_action).setOnClickListener { SearchActivity.start(a) }
        view.findViewById<RecyclerView>(R.id.screen_list).apply {
            layoutManager = LinearLayoutManager(a)
            adapter = this@CategoriesScreen.adapter
        }
        load(force = false)
    }

    private fun load(force: Boolean) {
        if (adapter.itemCount == 0) state.loading()
        Catalog.collections.load(force, activity.alive { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    adapter.submit(outcome.value)
                    if (outcome.value.isNotEmpty()) {
                        state.hide()
                    } else if (outcome.isFinal) {
                        state.message(
                            activity.getString(R.string.empty_collection_title),
                            activity.getString(R.string.empty_collection_text),
                            activity.getString(R.string.browse_all),
                        ) { ProductListActivity.all(activity) }
                    }
                }
                is Outcome.Failure -> state.message(
                    activity.getString(R.string.load_failed_title),
                    outcome.message,
                    activity.getString(R.string.try_again),
                ) { load(force = true) }
            }
        })
    }
}

// ---------------------------------------------------------------- Cart

class CartScreen(a: MainActivity) : Screen(a, R.layout.screen_list) {
    private val state = StateHolder(view.findViewById(R.id.screen_state))
    private val summary: View = view.findViewById(R.id.cart_summary)
    private val subtotal: TextView = view.findViewById(R.id.cart_subtotal)
    private val note: TextView = view.findViewById(R.id.cart_note)
    private val checkout: TextView = view.findViewById(R.id.cart_checkout)
    private val adapter = LineAdapter()
    private val cart get() = activity.app.cart

    init {
        view.findViewById<TextView>(R.id.screen_title).setText(R.string.cart_title)
        view.findViewById<View>(R.id.screen_action).show(false)
        view.findViewById<RecyclerView>(R.id.screen_list).apply {
            layoutManager = LinearLayoutManager(a)
            adapter = this@CartScreen.adapter
            itemAnimator = null
        }
        checkout.setOnClickListener {
            if (cart.lines.any { it.available }) {
                WebActivity.checkout(a, cart.checkoutUrl(), clearCartWhenPaid = true)
            }
        }
        render()
    }

    override fun onShown() {
        // Prices and stock may have changed since the items were added.
        Catalog.products.load(false, activity.alive { outcome ->
            if (outcome is Outcome.Success) cart.syncWith(outcome.value)
        })
        render()
    }

    fun render() {
        val lines = cart.lines
        adapter.submit(lines)
        if (lines.isEmpty()) {
            summary.show(false)
            state.message(
                activity.getString(R.string.cart_empty_title),
                activity.getString(R.string.cart_empty_text),
                activity.getString(R.string.start_shopping),
            ) { activity.select(MainActivity.TAB_HOME) }
            return
        }
        state.hide()
        summary.show(true)
        subtotal.text = Money.format(cart.subtotal)
        note.setText(if (cart.hasUnavailable) R.string.remove_sold_out else R.string.checkout_note)
        note.setTextColor(activity.getColor(if (cart.hasUnavailable) R.color.danger else R.color.text_secondary))
        val canCheckout = lines.any { it.available }
        checkout.isEnabled = canCheckout
        checkout.alpha = if (canCheckout) 1f else 0.4f
    }

    private inner class LineAdapter : RecyclerView.Adapter<LineHolder>() {
        private var items: List<CartLine> = emptyList()

        fun submit(list: List<CartLine>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineHolder {
            val row = activity.layoutInflater.inflate(R.layout.item_cart_line, parent, false)
            row.findViewById<View>(R.id.line_image).clipToOutline = true
            return LineHolder(row)
        }

        override fun onBindViewHolder(holder: LineHolder, position: Int) {
            val line = items[position]
            ImageLoader.load(holder.image, line.image, activity.dp(76))
            holder.title.text = line.title
            holder.variant.show(line.variantTitle.isNotEmpty() || !line.available)
            holder.variant.text = if (line.available) line.variantTitle else activity.getString(R.string.sold_out)
            holder.variant.setTextColor(
                activity.getColor(if (line.available) R.color.text_secondary else R.color.danger)
            )
            holder.stepper.show(line.available)
            holder.quantity.text = line.quantity.toString()
            holder.price.text = if (line.available) Money.format(line.total) else ""
            holder.itemView.alpha = if (line.available) 1f else 0.6f

            holder.minus.setOnClickListener { cart.setQuantity(line.variantId, line.quantity - 1) }
            holder.plus.setOnClickListener {
                if (line.quantity >= StoreConfig.MAX_QUANTITY) {
                    activity.toast(activity.getString(R.string.max_quantity, StoreConfig.MAX_QUANTITY))
                } else {
                    cart.setQuantity(line.variantId, line.quantity + 1)
                }
            }
            holder.remove.setOnClickListener { cart.remove(line.variantId) }
            val open = View.OnClickListener { ProductDetailActivity.start(activity, line.handle) }
            holder.image.setOnClickListener(open)
            holder.title.setOnClickListener(open)
        }
    }

    private class LineHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.line_image)
        val title: TextView = view.findViewById(R.id.line_title)
        val variant: TextView = view.findViewById(R.id.line_variant)
        val stepper: View = view.findViewById(R.id.line_stepper)
        val minus: View = view.findViewById(R.id.line_minus)
        val plus: View = view.findViewById(R.id.line_plus)
        val quantity: TextView = view.findViewById(R.id.line_quantity)
        val price: TextView = view.findViewById(R.id.line_price)
        val remove: View = view.findViewById(R.id.line_remove)
    }
}

// ---------------------------------------------------------------- More

class MoreScreen(a: MainActivity) : Screen(a, R.layout.screen_more) {
    private val container: ViewGroup = view.findViewById(R.id.more_container)

    init {
        header(R.string.more_shop)
        row(R.drawable.ic_person, R.string.more_account) { web(StoreConfig.ACCOUNT_URL, R.string.more_account) }
        row(R.drawable.ic_heart_outline, R.string.wishlist) { ProductListActivity.wishlist(a) }
        row(R.drawable.ic_star, R.string.more_rashi) { web(StoreConfig.RASHI_URL, R.string.more_rashi) }

        header(R.string.more_help)
        row(R.drawable.ic_chat, R.string.more_whatsapp) {
            Links.whatsApp(a, a.getString(R.string.whatsapp_general_message))
        }
        row(R.drawable.ic_phone, R.string.call_us) { Links.call(a) }
        row(R.drawable.ic_mail, R.string.more_email) { Links.email(a) }
        row(R.drawable.ic_info, R.string.more_contact) { web(StoreConfig.CONTACT_URL, R.string.more_contact) }

        header(R.string.more_store)
        text(R.string.store_address)
        text(R.string.store_hours)
        row(R.drawable.ic_place, R.string.get_directions) { Links.directions(a) }

        header(R.string.more_about_section)
        row(R.drawable.ic_gem, R.string.more_about) { web(StoreConfig.ABOUT_URL, R.string.more_about) }
        row(R.drawable.ic_document, R.string.more_blog) { web(StoreConfig.BLOG_URL, R.string.more_blog) }

        header(R.string.more_policies)
        row(R.drawable.ic_shipping, R.string.more_shipping) { web(StoreConfig.SHIPPING_URL, R.string.more_shipping) }
        row(R.drawable.ic_returns, R.string.more_refund) { web(StoreConfig.REFUND_URL, R.string.more_refund) }
        row(R.drawable.ic_verified, R.string.more_privacy) { web(StoreConfig.PRIVACY_URL, R.string.more_privacy) }
        row(R.drawable.ic_document, R.string.more_terms) { web(StoreConfig.TERMS_URL, R.string.more_terms) }

        val version = a.layoutInflater.inflate(R.layout.item_more_header, container, false) as TextView
        version.text = a.getString(R.string.version, BuildConfig.VERSION_NAME)
        version.isAllCaps = false
        version.setTextColor(a.getColor(R.color.text_secondary))
        container.addView(version)
    }

    private fun web(url: String, title: Int) = WebActivity.start(activity, url, activity.getString(title))

    private fun header(title: Int) {
        val header = activity.layoutInflater.inflate(R.layout.item_more_header, container, false) as TextView
        header.setText(title)
        container.addView(header)
    }

    private fun text(body: Int) {
        val block = activity.layoutInflater.inflate(R.layout.item_more_text, container, false) as TextView
        block.setText(body)
        container.addView(block)
    }

    private fun row(icon: Int, title: Int, onClick: () -> Unit) {
        val row = activity.layoutInflater.inflate(R.layout.item_more_row, container, false)
        row.findViewById<ImageView>(R.id.row_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.row_title).setText(title)
        row.setOnClickListener { onClick() }
        container.addView(row)
    }
}
