package com.sandhyagems.app

import android.app.Application
import android.content.Context
import com.sandhyagems.app.data.CartStore
import com.sandhyagems.app.data.Catalog
import com.sandhyagems.app.data.Wishlist
import com.sandhyagems.app.util.ImageLoader

class App : Application() {
    lateinit var cart: CartStore
        private set
    lateinit var wishlist: Wishlist
        private set

    override fun onCreate() {
        super.onCreate()
        Catalog.init(this)
        ImageLoader.init(this)
        cart = CartStore(getSharedPreferences("cart", Context.MODE_PRIVATE))
        wishlist = Wishlist(getSharedPreferences("wishlist", Context.MODE_PRIVATE))
    }
}
