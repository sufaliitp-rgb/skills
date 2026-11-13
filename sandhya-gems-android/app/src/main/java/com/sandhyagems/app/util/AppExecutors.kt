package com.sandhyagems.app.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object AppExecutors {
    /** Background work: network and disk. */
    val io: ExecutorService = Executors.newFixedThreadPool(3)

    /** Image downloads get their own threads so a slow photo never delays the catalogue. */
    val images: ExecutorService = Executors.newFixedThreadPool(4)

    val main = Handler(Looper.getMainLooper())
}
