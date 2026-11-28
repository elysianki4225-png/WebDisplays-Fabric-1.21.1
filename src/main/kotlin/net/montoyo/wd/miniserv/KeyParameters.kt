/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv

object KeyParameters {
    const val RSA_KEY_SIZE = 2048
    const val KEY_SIZE = 32
    const val CHALLENGE_SIZE = 32
    const val MAC_ALGORITHM = "HmacSHA256"
    const val RSA_CIPHER = "RSA/ECB/PKCS1Padding"
}
