package com.terrapaver.client

object TerraPaverClientHooks {
    @Volatile
    var openSettingsCallback: (() -> Unit)? = null

    @Volatile
    var openBiomeCallback: (() -> Unit)? = null

    fun openSettingsScreen() {
        openSettingsCallback?.invoke()
    }

    fun openBiomeScreen() {
        openBiomeCallback?.invoke()
    }
}
