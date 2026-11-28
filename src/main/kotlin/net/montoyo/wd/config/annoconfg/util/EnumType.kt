package net.montoyo.wd.config.annoconfg.util

enum class EnumType(val clazz: Class<*>) {
    BYTE(java.lang.Byte.TYPE),
    SHORT(java.lang.Short.TYPE),
    INT(Integer.TYPE),
    LONG(java.lang.Long.TYPE),
    FLOAT(java.lang.Float.TYPE),
    DOUBLE(java.lang.Double.TYPE),
    BOOLEAN(java.lang.Boolean.TYPE),
    OTHER(Any::class.java);

    companion object {
        @JvmStatic
        fun forClass(clazz: Class<*>): EnumType {
            for (value in entries) {
                if (value.clazz == clazz) return value
            }
            return OTHER
        }
    }
}
