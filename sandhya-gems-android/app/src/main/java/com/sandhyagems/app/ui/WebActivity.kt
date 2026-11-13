package com.sandhyagems.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import com.sandhyagems.app.BuildConfig
import com.sandhyagems.app.R
import com.sandhyagems.app.data.Shopify

/**
 * Shows a page of the website inside the app: the store's own secure checkout,
 * the customer's account, and the policy pages. Payment happens on the store's
 * checkout exactly as it does in a browser; the app never sees card details.
 */
class WebActivity : BaseActivity() {
    private var webView: WebView? = null
    private lateinit var state: StateHolder
    private lateinit var progress: ProgressBar
    private var clearCartWhenPaid = false
    private var orderPlaced = false
    private var failed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)
        applyInsets(findViewById(R.id.root))
        setupToolbar(intent.getStringExtra(EXTRA_TITLE).orEmpty(), R.drawable.ic_close)
        state = StateHolder(findViewById(R.id.state))
        progress = findViewById(R.id.web_progress)
        clearCartWhenPaid = intent.getBooleanExtra(EXTRA_CLEAR_CART, false)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null || !url.startsWith("https://")) {
            finish()
            return
        }

        val view = try {
            WebView(this)
        } catch (e: Exception) {
            // The phone's WebView component is missing or being updated: use the browser instead.
            Links.open(this, Uri.parse(url))
            finish()
            return
        }
        webView = view
        findViewById<ViewGroup>(R.id.web_container).addView(
            view, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        view.settings.apply {
            javaScriptEnabled = true // the checkout does not work without it
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString SandhyaGemsApp/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true) // payment gateways need these
        }
        view.webViewClient = Client()
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.show(newProgress < 100)
            }
        }

        if (savedInstanceState == null || view.restoreState(savedInstanceState) == null) view.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val view = webView
        // After an order is placed, Back should leave rather than step back into the paid checkout.
        if (view != null && view.canGoBack() && !orderPlaced) view.goBack() else super.onBackPressed()
    }

    private fun checkOrderPlaced(url: String?) {
        if (orderPlaced || !Shopify.isOrderComplete(url)) return
        if (!Shopify.isStoreHost(Uri.parse(url).host) && Uri.parse(url).host?.endsWith("shopify.com") != true) return
        orderPlaced = true
        if (clearCartWhenPaid) app.cart.clear()
        toast(R.string.order_placed)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "https" -> false
                "http" -> {
                    // Never load an unencrypted page here: ask for the secure version instead.
                    view.loadUrl(uri.buildUpon().scheme("https").build().toString())
                    true
                }
                "intent" -> {
                    openIntentUri(view, uri.toString())
                    true
                }
                // upi:, tel:, mailto:, whatsapp: and other app links (UPI apps, bank apps).
                else -> {
                    Links.open(this@WebActivity, uri)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            failed = false
            checkOrderPlaced(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            checkOrderPlaced(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (!failed) {
                state.hide()
                view.show(true)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            failed = true
            view.show(false)
            state.message(
                getString(R.string.load_failed_title),
                getString(R.string.page_failed_text),
                getString(R.string.try_again),
            ) {
                state.loading()
                view.reload()
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The page's process was killed (usually low memory). Close this screen instead of crashing.
            finish()
            return true
        }
    }

    /** Payment pages use intent: links to hand over to a UPI or bank app. */
    private fun openIntentUri(view: WebView, raw: String) {
        val intent = try {
            Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            return
        }
        // A web page must only be able to open another app's public, browsable screens.
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        val opened = try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
        if (!opened) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && fallback.startsWith("https://")) view.loadUrl(fallback)
            else toast(R.string.no_app_found)
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CLEAR_CART = "clear_cart"

        fun start(context: Context, url: String, title: String) = context.startActivity(
            Intent(context, WebActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title)
        )

        fun checkout(context: Context, url: String, clearCartWhenPaid: Boolean) = context.startActivity(
            Intent(context, WebActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, context.getString(R.string.checkout))
                .putExtra(EXTRA_CLEAR_CART, clearCartWhenPaid)
        )
    }
}
