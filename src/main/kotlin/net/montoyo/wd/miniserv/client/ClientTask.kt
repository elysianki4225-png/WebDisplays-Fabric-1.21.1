/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv.client

import net.montoyo.wd.WebDisplays
import java.util.function.Consumer

abstract class ClientTask<T : ClientTask<T>> {
    private var finishCallback: Consumer<T>? = null

    @Volatile
    private var canceled: Boolean = false

    @JvmField
    protected var runCallbackOnMcThread: Boolean = false

    @JvmField
    protected val client: Client = Client.getInstance()

    abstract fun start()

    abstract fun abort()

    @Suppress("UNCHECKED_CAST")
    fun onFinished() {
        // Called by Client, don't call it from a ClientTask!
        val cb = finishCallback
        if (cb != null && !isCanceled()) {
            if (runCallbackOnMcThread) {
                WebDisplays.PROXY.enqueue { cb.accept(this as T) }
            } else {
                cb.accept(this as T)
            }
        }
    }

    fun setFinishCallback(finishCallback: Consumer<T>?) {
        this.finishCallback = finishCallback
    }

    fun setRunCallbackOnMinecraftThread(runCallbackOnMcThread: Boolean) {
        this.runCallbackOnMcThread = runCallbackOnMcThread
    }

    fun cancel() {
        synchronized(this) {
            canceled = true
        }
        Client.getInstance().wakeup()
    }

    fun isCanceled(): Boolean {
        synchronized(this) {
            return canceled
        }
    }
}
