package com.sandhyagems.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.sandhyagems.app.R
import com.sandhyagems.app.data.Catalog
import com.sandhyagems.app.data.Outcome
import com.sandhyagems.app.data.Product
import com.sandhyagems.app.data.Shopify
import com.sandhyagems.app.data.Variant
import com.sandhyagems.app.util.ImageLoader
import com.sandhyagems.app.util.Money

class ProductDetailActivity : BaseActivity() {
    private lateinit var state: StateHolder
    private lateinit var heart: ImageView
    private lateinit var handle: String
    private var product: Product? = null
    private var variant: Variant? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)
        applyInsets(findViewById(R.id.root))
        handle = intent.getStringExtra(EXTRA_HANDLE).orEmpty()

        val (share, wish) = setupToolbar("")
        heart = wish
        share.setImageResource(R.drawable.ic_share)
        share.contentDescription = getString(R.string.share_product)
        share.setOnClickListener { product?.let { Links.share(this, it.title + "\n" + it.url) } }
        heart.contentDescription = getString(R.string.wishlist)
        heart.setOnClickListener {
            val saved = app.wishlist.toggle(handle)
            toast(if (saved) R.string.saved_to_wishlist else R.string.removed_from_wishlist)
            updateHeart()
        }
        share.show(true)
        heart.show(true)
        updateHeart()

        state = StateHolder(findViewById(R.id.state))
        load(force = false)
    }

    override fun onStart() {
        super.onStart()
        updateAddButton()
    }

    private fun load(force: Boolean) {
        val known = Catalog.find(handle)
        if (known != null && !force) {
            bind(known)
            return
        }
        state.loading()
        Catalog.products.load(force, alive { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    val found = outcome.value.firstOrNull { it.handle == handle } ?: Catalog.find(handle)
                    when {
                        found != null -> bind(found)
                        outcome.isFinal -> state.message(
                            getString(R.string.product_missing_title),
                            getString(R.string.product_missing_text),
                            getString(R.string.browse_all),
                        ) {
                            ProductListActivity.all(this)
                            finish()
                        }
                    }
                }
                is Outcome.Failure -> state.message(
                    getString(R.string.load_failed_title),
                    outcome.message,
                    getString(R.string.try_again),
                ) { load(force = true) }
            }
        })
    }

    private fun bind(p: Product) {
        product = p
        state.hide()
        findViewById<View>(R.id.product_scroll).show(true)
        findViewById<View>(R.id.product_actions).show(true)
        findViewById<TextView>(R.id.product_title).text = p.title

        bindGallery(p)
        bindOptions(p)
        selectVariant(variant?.let { old -> p.variants.firstOrNull { it.id == old.id } } ?: p.defaultVariant)

        val description = HtmlCompat.fromHtml(p.descriptionHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).trim()
        findViewById<View>(R.id.product_description_label).show(description.isNotEmpty())
        findViewById<TextView>(R.id.product_description).apply {
            show(description.isNotEmpty())
            text = description
            movementMethod = LinkMovementMethod.getInstance()
        }

        findViewById<View>(R.id.product_ask).setOnClickListener {
            Links.whatsApp(this, getString(R.string.whatsapp_product_message, p.title, p.url))
        }
        findViewById<View>(R.id.product_add).setOnClickListener { onAddClicked() }
        findViewById<View>(R.id.product_buy).setOnClickListener {
            val v = variant?.takeIf { it.available } ?: return@setOnClickListener
            WebActivity.checkout(this, Shopify.checkoutUrl(listOf(v.id to 1)), clearCartWhenPaid = false)
        }
    }

    private fun bindGallery(p: Product) {
        val gallery = findViewById<RecyclerView>(R.id.product_gallery)
        val counter = findViewById<TextView>(R.id.product_gallery_count)
        val manager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        gallery.layoutManager = manager
        gallery.adapter = GalleryAdapter(p.images)
        if (gallery.onFlingListener == null) PagerSnapHelper().attachToRecyclerView(gallery)
        counter.show(p.images.size > 1)
        counter.text = "1 / ${p.images.size}"
        gallery.clearOnScrollListeners()
        gallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val position = manager.findFirstCompletelyVisibleItemPosition()
                if (position >= 0) counter.text = "${position + 1} / ${p.images.size}"
            }
        })
    }

    private fun bindOptions(p: Product) {
        val hasChoices = p.variants.size > 1
        findViewById<View>(R.id.product_options_label).show(hasChoices)
        findViewById<View>(R.id.product_options_scroll).show(hasChoices)
        val container = findViewById<ViewGroup>(R.id.product_options)
        container.removeAllViews()
        if (!hasChoices) return
        p.variants.forEach { v ->
            val chip = layoutInflater.inflate(R.layout.view_chip, container, false) as TextView
            chip.text = v.title
            chip.tag = v.id
            chip.alpha = if (v.available) 1f else 0.45f
            chip.setOnClickListener { selectVariant(v) }
            container.addView(chip)
        }
    }

    private fun selectVariant(v: Variant) {
        variant = v
        val container = findViewById<ViewGroup>(R.id.product_options)
        for (i in 0 until container.childCount) {
            val chip = container.getChildAt(i)
            chip.isSelected = chip.tag == v.id
        }

        findViewById<TextView>(R.id.product_price).text = Money.format(v.price)
        val discount = Money.discountPercent(v.price, v.compareAtPrice)
        findViewById<TextView>(R.id.product_compare).apply {
            show(discount != null)
            if (discount != null) {
                text = Money.format(v.compareAtPrice!!)
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }
        }
        findViewById<TextView>(R.id.product_discount).apply {
            show(discount != null)
            if (discount != null) text = getString(R.string.percent_off, discount)
        }
        findViewById<TextView>(R.id.product_stock).apply {
            setText(if (v.available) R.string.in_stock else R.string.sold_out)
            setTextColor(getColor(if (v.available) R.color.success else R.color.danger))
        }
        findViewById<View>(R.id.product_buy).apply {
            isEnabled = v.available
            alpha = if (v.available) 1f else 0.4f
        }
        updateAddButton()
    }

    private fun onAddClicked() {
        val p = product ?: return
        val v = variant ?: return
        if (app.cart.quantityOf(v.id) > 0) {
            MainActivity.open(this, MainActivity.TAB_CART)
            return
        }
        if (!v.available) return
        app.cart.add(p, v)
        toast(R.string.added_to_cart)
        updateAddButton()
    }

    /** "Add to cart" becomes "Go to cart" once the chosen stone is in the cart. */
    private fun updateAddButton() {
        val v = variant ?: return
        val inCart = app.cart.quantityOf(v.id) > 0
        findViewById<TextView>(R.id.product_add).apply {
            setText(
                when {
                    inCart -> R.string.go_to_cart
                    v.available -> R.string.add_to_cart
                    else -> R.string.sold_out
                }
            )
            isEnabled = inCart || v.available
            alpha = if (isEnabled) 1f else 0.4f
        }
    }

    private fun updateHeart() {
        val saved = app.wishlist.contains(handle)
        heart.setImageResource(if (saved) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        heart.setColorFilter(getColor(if (saved) R.color.primary else R.color.text))
    }

    private inner class GalleryAdapter(private val images: List<String>) :
        RecyclerView.Adapter<GalleryAdapter.Holder>() {

        // With no photo at all, still show one empty tile so the layout keeps its shape.
        override fun getItemCount(): Int = maxOf(1, images.size)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(layoutInflater.inflate(R.layout.item_gallery_image, parent, false) as ImageView)

        override fun onBindViewHolder(holder: Holder, position: Int) {
            ImageLoader.load(holder.image, images.getOrNull(position), resources.displayMetrics.widthPixels)
        }

        inner class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)
    }

    companion object {
        private const val EXTRA_HANDLE = "handle"

        fun start(context: Context, handle: String) = context.startActivity(
            Intent(context, ProductDetailActivity::class.java).putExtra(EXTRA_HANDLE, handle)
        )
    }
}
