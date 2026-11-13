package com.sandhyagems.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sandhyagems.app.R
import com.sandhyagems.app.data.Catalog
import com.sandhyagems.app.data.Outcome
import com.sandhyagems.app.data.Product

/** Search as you type. Runs on the phone over the loaded catalogue, so it is instant. */
class SearchActivity : BaseActivity() {
    private lateinit var input: EditText
    private lateinit var clear: View
    private lateinit var chips: ViewGroup
    private lateinit var chipsScroll: View
    private lateinit var state: StateHolder
    private val adapter = ProductAdapter { ProductDetailActivity.start(this, it.handle) }
    private var products: List<Product>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        applyInsets(findViewById(R.id.root))

        input = findViewById(R.id.search_input)
        clear = findViewById(R.id.search_clear)
        chips = findViewById(R.id.search_chips)
        chipsScroll = findViewById(R.id.search_chips_scroll)
        state = StateHolder(findViewById(R.id.state))
        findViewById<View>(R.id.search_back).setOnClickListener { finish() }
        clear.setOnClickListener { input.setText("") }

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = GridLayoutManager(this, 2)
        list.adapter = adapter
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = render()
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) hideKeyboard()
            true
        }
        input.requestFocus()
        load(force = false)
    }

    private fun load(force: Boolean) {
        if (products == null) state.loading()
        Catalog.products.load(force, alive { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    products = outcome.value
                    buildChips(outcome.value)
                    render()
                }
                is Outcome.Failure -> state.message(
                    getString(R.string.load_failed_title),
                    outcome.message,
                    getString(R.string.try_again),
                ) { load(force = true) }
            }
        })
    }

    /** Quick shortcuts for the most common stone types in the catalogue. */
    private fun buildChips(all: List<Product>) {
        val types = all.map { it.productType.trim() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key }
        chips.removeAllViews()
        types.forEach { type ->
            val chip = layoutInflater.inflate(R.layout.view_chip, chips, false) as TextView
            chip.text = type
            chip.setOnClickListener {
                input.setText(type)
                input.setSelection(type.length)
                hideKeyboard()
            }
            chips.addView(chip)
        }
        chipsScroll.show(types.isNotEmpty())
    }

    private fun render() {
        val query = input.text.toString()
        clear.visibility = if (query.isEmpty()) View.INVISIBLE else View.VISIBLE
        val all = products ?: return
        val results = Catalog.search(all, query)
        adapter.submit(results)
        if (results.isEmpty()) {
            state.message(getString(R.string.no_results_title), getString(R.string.no_results_text))
        } else {
            state.hide()
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
    }

    companion object {
        fun start(context: Context) = context.startActivity(Intent(context, SearchActivity::class.java))
    }
}
