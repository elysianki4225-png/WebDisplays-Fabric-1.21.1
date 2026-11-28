/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.miniserv

enum class PacketID {
    INIT_CONN,          // C->S
    AUTHENTICATE,       // C->S and S->C
    PING,               // C->S and S->C
    BEGIN_FILE_UPLOAD,  // C->S
    FILE_PART,          // C->S and S->C
    FILE_STATUS,        // S->C
    GET_FILE,           // C->S
    QUOTA,              // C->S and S->C
    LIST,               // C->S and S->C
    DELETE;             // C->S and S->C

    companion object {
        @JvmStatic
        fun fromInt(i: Int): PacketID? =
            if (i < 0 || i >= entries.size) null else entries[i]
    }
}
