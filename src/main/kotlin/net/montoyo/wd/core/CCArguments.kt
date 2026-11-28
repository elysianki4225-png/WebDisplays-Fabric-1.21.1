/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

class CCArguments(private val args: Array<Any?>) : IComputerArgs {
    override fun checkString(i: Int): String {
        checkIndex(i, "string")
        val obj = args[i]
        if (obj !is String) throw typeError(i, "string", obj)
        return obj
    }

    override fun checkInteger(i: Int): Int {
        checkIndex(i, "number")
        val obj = args[i]
        return when (obj) {
            is Int -> obj
            is Double -> obj.toInt()
            is Float -> obj.toInt()
            else -> throw typeError(i, "number", obj)
        }
    }

    override fun checkTable(i: Int): Map<*, *> {
        checkIndex(i, "table")
        val obj = args[i]
        if (obj !is Map<*, *>) throw typeError(i, "table", obj)
        return obj
    }

    private fun checkIndex(idx: Int, want: String) {
        if (idx < 0 || idx >= args.size) {
            throw typeError(idx, want, null)
        }
    }

    override fun count(): Int = args.size

    companion object {
        private fun typeError(idx: Int, want: String, got: Any?): IllegalArgumentException =
            IllegalArgumentException("bad argument #${idx + 1} ($want expected, got ${luaTypeName(got)})")

        private fun luaTypeName(obj: Any?): String {
            if (obj == null) return "nil"
            val cls: Class<*> = obj.javaClass
            return when {
                cls == java.lang.Boolean::class.java || cls == java.lang.Boolean.TYPE -> "boolean"
                cls == Integer::class.java || cls == Integer.TYPE ||
                    cls == java.lang.Double::class.java || cls == java.lang.Double.TYPE ||
                    cls == java.lang.Float::class.java || cls == java.lang.Float.TYPE -> "number"
                cls == String::class.java -> "string"
                Map::class.java.isAssignableFrom(cls) -> "table"
                else -> cls.simpleName
            }
        }
    }
}
