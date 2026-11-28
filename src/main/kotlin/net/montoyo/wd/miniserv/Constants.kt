/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv

object Constants {
    const val FUPA_STATUS_BAD_NAME = 1
    const val FUPA_STATUS_INVALID_SIZE = 2
    const val FUPA_STATUS_EXCEEDS_QUOTA = 3
    const val FUPA_STATUS_OCCUPIED = 4
    const val FUPA_STATUS_FILE_EXISTS = 5
    const val FUPA_STATUS_INTERNAL_ERROR = 6
    const val FUPA_STATUS_USER_ABORT = 7
    const val FUPA_STATUS_LIER = 8
    const val FUPA_STATUS_CONNECTION_LOST = 9

    const val GETF_STATUS_BAD_NAME = 1
    const val GETF_STATUS_NOT_FOUND = 2
    const val GETF_STATUS_INTERNAL_ERROR = 3
    const val GETF_STATUS_CONNECTION_LOST = 4
    const val GETF_STATUS_TIMED_OUT = 5

    const val CLIENT_TIMEOUT = 30000L
    const val CLIENT_PING_PERIOD = 5000L
}
