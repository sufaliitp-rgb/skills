package com.sandhyagems.app.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sandhyagems.app.R
import com.sandhyagems.app.data.Product
import com.sandhyagems.app.data.ShopCollection
import com.sandhyagems.app.util.ImageLoader
import com.sandhyagems.app.util.Money

/**
 * Product cards. In a grid the card fills its column; pass [cardWidthDp] for a
 * fixed-width card in a sideways-scrolling row.
 */
class ProductAdapter(
    private val cardWidthDp: Int? = null,
    private val onClick: (Product) -> Unit,
) : RecyclerView.Adapter<ProductAdapter.Holder>() {

    private var items: List<Product> = emptyList()

    fun submit(list: List<Product>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product_card, parent, false)
        cardWidthDp?.let { view.layoutParams.width = parent.context.dp(it) }
        view.findViewById<View>(R.id.product_image).clipToOutline = true
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = items[position]
        val context = holder.itemView.context
        val variant = product.defaultVariant
        val imageWidth = cardWidthDp?.let { context.dp(it) } ?: (context.resources.displayMetrics.widthPixels / 2)

        ImageLoader.load(holder.image, product.firstImage, imageWidth)
        holder.title.text = product.title
        val price = Money.format(variant.price)
        holder.price.text = if (product.hasPriceRange) context.getString(R.string.price_from, price) else price

        val discount = Money.discountPercent(variant.price, variant.compareAtPrice)
        holder.compare.show(discount != null)
        if (discount != null) {
            holder.compare.text = Money.format(variant.compareAtPrice!!)
            holder.compare.paintFlags = holder.compare.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        holder.scrim.show(!product.available)
        when {
            !product.available -> {
                holder.badge.show(true)
                holder.badge.setBackgroundResource(R.drawable.bg_badge_muted)
                holder.badge.setText(R.string.sold_out)
            }
            discount != null -> {
                holder.badge.show(true)
                holder.badge.setBackgroundResource(R.drawable.bg_badge)
                holder.badge.text = context.getString(R.string.percent_off, discount)
            }
            else -> holder.badge.show(false)
        }
        holder.itemView.setOnClickListener { onClick(product) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.product_image)
        val scrim: View = view.findViewById(R.id.product_scrim)
        val badge: TextView = view.findViewById(R.id.product_badge)
        val title: TextView = view.findViewById(R.id.product_title)
        val price: TextView = view.findViewById(R.id.product_price)
        val compare: TextView = view.findViewById(R.id.product_compare)
    }
}

/** Collections, either as round thumbnails (home row) or as full-width tiles (categories tab). */
class CategoryAdapter(
    private val circles: Boolean,
    private val onClick: (ShopCollection) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.Holder>() {

    private var items: List<ShopCollection> = emptyList()

    fun submit(list: List<ShopCollection>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (circles) R.layout.item_category_circle else R.layout.item_category_tile
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        view.findViewById<View>(R.id.category_image).clipToOutline = true
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val collection = items[position]
        val context = holder.itemView.context
        holder.title.text = collection.title
        ImageLoader.load(holder.image, collection.image, context.dp(72))
        holder.image.clearColorFilter()
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        if (collection.image == null) {
            // No picture set in the store: use our own photo of that stone, else the gem mark.
            val photo = CategoryArt.forTitle(collection.title)
            if (photo != null) {
                holder.image.setImageResource(photo)
            } else {
                holder.image.setImageResource(R.drawable.ic_gem)
                holder.image.setColorFilter(context.getColor(R.color.gold))
                holder.image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        holder.count?.let { count ->
            count.show(collection.productsCount > 0)
            count.text = if (collection.productsCount == 1) context.getString(R.string.one_item)
            else context.getString(R.string.items_count, collection.productsCount)
        }
        holder.itemView.setOnClickListener { onClick(collection) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.category_image)
        val title: TextView = view.findViewById(R.id.category_title)
        val count: TextView? = view.findViewById(R.id.category_count)
    }
}
