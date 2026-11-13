package com.sandhyagems.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sandhyagems.app.R
import com.sandhyagems.app.data.Catalog
import com.sandhyagems.app.data.Outcome
import com.sandhyagems.app.data.Product

/** A two-column grid of products: one collection, the whole catalogue, or the wishlist. */
class ProductListActivity : BaseActivity() {
    private enum class Sort(val label: Int) {
        FEATURED(R.string.sort_featured),
        PRICE_LOW(R.string.sort_price_low),
        PRICE_HIGH(R.string.sort_price_high),
        NEWEST(R.string.sort_newest),
    }

    private lateinit var state: StateHolder
    private lateinit var bar: View
    private lateinit var count: TextView
    private val adapter = ProductAdapter { ProductDetailActivity.start(this, it.handle) }

    private var mode = MODE_ALL
    private var handle: String? = null
    private var source: List<Product> = emptyList()
    private var loaded = false
    private var sort = Sort.FEATURED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_list)
        applyInsets(findViewById(R.id.root))

        mode = intent.getIntExtra(EXTRA_MODE, MODE_ALL)
        handle = intent.getStringExtra(EXTRA_HANDLE)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: when (mode) {
            MODE_WISHLIST -> getString(R.string.wishlist)
            MODE_COLLECTION -> handle.orEmpty().replace('-', ' ').replaceFirstChar { it.uppercase() }
            else -> getString(R.string.all_gemstones)
        }
        val (search, _) = setupToolbar(title)
        search.setImageResource(R.drawable.ic_search)
        search.contentDescription = getString(R.string.search)
        search.show(true)
        search.setOnClickListener { SearchActivity.start(this) }

        state = StateHolder(findViewById(R.id.state))
        bar = findViewById(R.id.list_bar)
        count = findViewById(R.id.list_count)
        findViewById<View>(R.id.list_sort).setOnClickListener { chooseSort() }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = GridLayoutManager(this@ProductListActivity, 2)
            adapter = this@ProductListActivity.adapter
        }
        savedInstanceState?.getInt(STATE_SORT)?.let { sort = Sort.values()[it] }
        load(force = false)
    }

    override fun onStart() {
        super.onStart()
        // A stone may have been removed from the wishlist on the product page.
        if (mode == MODE_WISHLIST && loaded) render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SORT, sort.ordinal)
    }

    private fun load(force: Boolean) {
        if (!loaded) state.loading()
        val resource = if (mode == MODE_COLLECTION) Catalog.collection(handle.orEmpty()) else Catalog.products
        resource.load(force, alive { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    source = outcome.value
                    loaded = true
                    // Wait for the fresh copy before declaring a list empty.
                    if (source.isNotEmpty() || outcome.isFinal) render()
                }
                is Outcome.Failure -> {
                    bar.show(false)
                    state.message(
                        getString(R.string.load_failed_title),
                        outcome.message,
                        getString(R.string.try_again),
                    ) { load(force = true) }
                }
            }
        })
    }

    private fun render() {
        val visible = if (mode == MODE_WISHLIST) source.filter { app.wishlist.contains(it.handle) } else source
        val sorted = when (sort) {
            Sort.FEATURED -> visible
            Sort.PRICE_LOW -> visible.sortedBy { it.defaultVariant.price }
            Sort.PRICE_HIGH -> visible.sortedByDescending { it.defaultVariant.price }
            Sort.NEWEST -> visible.sortedByDescending { it.publishedAt }
        }
        adapter.submit(sorted)
        bar.show(sorted.isNotEmpty())
        count.text = if (sorted.size == 1) getString(R.string.one_item) else getString(R.string.items_count, sorted.size)
        when {
            sorted.isNotEmpty() -> state.hide()
            mode == MODE_WISHLIST -> state.message(
                getString(R.string.wishlist_empty_title),
                getString(R.string.wishlist_empty_text),
                getString(R.string.browse_all),
            ) {
                all(this)
                finish()
            }
            else -> state.message(
                getString(R.string.empty_collection_title),
                getString(R.string.empty_collection_text),
            )
        }
    }

    private fun chooseSort() {
        val options = Sort.values()
        AlertDialog.Builder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(options.map { getString(it.label) }.toTypedArray(), sort.ordinal) { dialog, which ->
                sort = options[which]
                dialog.dismiss()
                render()
                findViewById<RecyclerView>(R.id.list).scrollToPosition(0)
            }
            .show()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_HANDLE = "handle"
        private const val EXTRA_TITLE = "title"
        private const val STATE_SORT = "sort"
        private const val MODE_ALL = 0
        private const val MODE_COLLECTION = 1
        private const val MODE_WISHLIST = 2

        fun all(context: Context) = context.startActivity(intent(context, MODE_ALL))

        fun wishlist(context: Context) = context.startActivity(intent(context, MODE_WISHLIST))

        fun collection(context: Context, handle: String, title: String?) = context.startActivity(
            intent(context, MODE_COLLECTION).putExtra(EXTRA_HANDLE, handle).putExtra(EXTRA_TITLE, title)
        )

        private fun intent(context: Context, mode: Int) =
            Intent(context, ProductListActivity::class.java).putExtra(EXTRA_MODE, mode)
    }
}
