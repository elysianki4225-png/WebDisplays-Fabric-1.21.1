package com.terrapaver.client

object TerraPaverClientHooks {
    @Volatile
    var openSettingsCallback: (() -> Unit)? = null

    fun openSettingsScreen() {
        openSettingsCallback?.invoke()
    }
}
