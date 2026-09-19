package ru.bleyzos.ai.api

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/** Все колбэки API приходят в главном потоке; сетевая работа — в общем пуле. */
internal object MainThread {
    private val handler = Handler(Looper.getMainLooper())

    fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    val io = Executors.newCachedThreadPool(object : ThreadFactory {
        private var n = 0
        @Synchronized
        override fun newThread(r: Runnable): Thread =
            Thread(r, "bleyzos-io-${n++}").apply { isDaemon = true }
    })
}

/** Ручка отмены длительной операции. */
fun interface Cancelable {
    fun cancel()
}
