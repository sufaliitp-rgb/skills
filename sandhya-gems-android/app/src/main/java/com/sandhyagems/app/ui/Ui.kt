package com.sandhyagems.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.sandhyagems.app.R
import com.sandhyagems.app.StoreConfig

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun Context.toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** A frame that is always as tall as it is wide, for product photos. */
class SquareFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

/** The loading spinner / message / retry block shared by every screen. */
class StateHolder(private val root: View) {
    private val progress: View = root.findViewById(R.id.state_progress)
    private val message: View = root.findViewById(R.id.state_message)
    private val title: TextView = root.findViewById(R.id.state_title)
    private val text: TextView = root.findViewById(R.id.state_text)
    private val action: TextView = root.findViewById(R.id.state_action)

    val isShowing: Boolean get() = root.visibility == View.VISIBLE

    fun loading() {
        root.show(true)
        progress.show(true)
        message.show(false)
    }

    fun hide() = root.show(false)

    fun message(titleText: String, bodyText: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
        root.show(true)
        progress.show(false)
        message.show(true)
        title.text = titleText
        text.text = bodyText
        action.show(actionText != null)
        action.text = actionText
        action.setOnClickListener { onAction?.invoke() }
    }
}

/** Sets up the shared toolbar and returns its two optional action buttons. */
fun Activity.setupToolbar(title: CharSequence, backIcon: Int = R.drawable.ic_back): Pair<ImageView, ImageView> {
    findViewById<TextView>(R.id.toolbar_title).text = title
    findViewById<ImageView>(R.id.toolbar_back).apply {
        setImageResource(backIcon)
        contentDescription = getString(if (backIcon == R.drawable.ic_close) R.string.close else R.string.back)
        setOnClickListener { finish() }
    }
    return findViewById<ImageView>(R.id.toolbar_action1) to findViewById(R.id.toolbar_action2)
}

/** Leaving the app: WhatsApp, the dialler, email, and the share sheet. */
object Links {
    fun whatsApp(context: Context, message: String) = open(
        context,
        Uri.parse("https://wa.me/${StoreConfig.WHATSAPP_NUMBER}?text=${Uri.encode(message)}")
    )

    fun call(context: Context) = open(context, Uri.parse("tel:${StoreConfig.PHONE}"), Intent.ACTION_DIAL)

    fun email(context: Context) = open(context, Uri.parse("mailto:${StoreConfig.EMAIL}"), Intent.ACTION_SENDTO)

    fun directions(context: Context) = open(
        context,
        Uri.parse(
            "geo:${StoreConfig.SHOP_LATITUDE},${StoreConfig.SHOP_LONGITUDE}?q=${Uri.encode(StoreConfig.SHOP_MAP_LABEL)}"
        )
    )

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, null))
    }

    fun open(context: Context, uri: Uri, action: String = Intent.ACTION_VIEW): Boolean =
        start(context, Intent(action, uri))

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        context.toast(R.string.no_app_found)
        false
    } catch (e: SecurityException) {
        context.toast(R.string.no_app_found)
        false
    }
}
