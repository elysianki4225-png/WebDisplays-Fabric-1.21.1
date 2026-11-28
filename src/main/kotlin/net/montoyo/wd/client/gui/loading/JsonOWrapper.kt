/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.loading

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.montoyo.wd.client.gui.controls.Control

class JsonOWrapper(
    private val `object`: JsonObject,
    private val variables: Map<String, Double>
) {
    fun getString(key: String, def: String?): String? {
        return if (`object`.has(key)) `object`.get(key).asString else def
    }

    fun getLong(key: String, def: Long): Long {
        return if (`object`.has(key)) `object`.get(key).asLong else def
    }

    fun getInt(key: String, def: Int): Int {
        if (!`object`.has(key)) return def
        val prim = `object`.get(key).asJsonPrimitive
        return if (prim.isNumber) prim.asInt else evalExpr(prim.asString, variables).toInt()
    }

    fun getFloat(key: String, def: Float): Float {
        if (!`object`.has(key)) return def
        val prim = `object`.get(key).asJsonPrimitive
        return if (prim.isNumber) prim.asFloat else evalExpr(prim.asString, variables).toFloat()
    }

    fun getDouble(key: String, def: Double): Double {
        if (!`object`.has(key)) return def
        val prim = `object`.get(key).asJsonPrimitive
        return if (prim.isNumber) prim.asDouble else evalExpr(prim.asString, variables)
    }

    fun getBool(key: String, def: Boolean): Boolean {
        if (!`object`.has(key)) return def
        val prim = `object`.get(key).asJsonPrimitive
        if (prim.isBoolean) return prim.asBoolean
        if (prim.isNumber) return prim.asInt != 0
        return evalExpr(prim.asString, variables) != 0.0
    }

    fun getObject(key: String): JsonOWrapper {
        return JsonOWrapper(if (`object`.has(key)) `object`.get(key).asJsonObject else JsonObject(), variables)
    }

    fun getArray(key: String): JsonAWrapper {
        return JsonAWrapper(if (`object`.has(key)) `object`.get(key).asJsonArray else JsonArray(), variables)
    }

    fun getObject(): JsonObject = `object`

    fun getColor(key: String, def: Int): Int {
        if (!`object`.has(key)) return def
        val c = `object`.get(key)
        if (c.isJsonPrimitive) {
            val prim = c.asJsonPrimitive
            if (prim.isNumber) return prim.asLong.toInt()
            if (prim.isString) {
                var str = prim.asString
                val dc = defaultColors[str.lowercase()]
                if (dc != null) return dc
                if (str.isNotEmpty() && str[0] == '#') str = str.substring(1)
                var ret = str.toLong(16)
                if (str.length <= 6) ret = ret or 0xFF000000L
                return ret.toInt()
            }
            return def
        }

        val r: Int
        val g: Int
        val b: Int
        val a: Int
        if (c.isJsonArray) {
            val array = c.asJsonArray
            r = array.get(0).asInt
            g = array.get(1).asInt
            b = array.get(2).asInt
            a = if (array.size() >= 4) array.get(3).asInt else 255
        } else if (c.isJsonObject) {
            val obj = c.asJsonObject
            r = obj.get("r").asInt
            g = obj.get("g").asInt
            b = obj.get("b").asInt
            a = if (obj.has("a")) obj.get("a").asInt else 255
        } else {
            return def
        }

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private class VarOpPair {
        var `var` = 0.0
        var op = '\u0000'

        fun setVar(str: String, isNumber: Boolean, expr: String, variables: Map<String, Double>) {
            if (isNumber) {
                `var` = str.toDouble()
            } else {
                val neg = str[0] == '-'
                val varName = if (neg) str.substring(1) else str
                val d = variables[varName]
                    ?: throw RuntimeException("Unknown variable \"$varName\" in expression \"$expr\"")
                `var` = if (neg) -d else d
            }
        }
    }

    companion object {
        private val defaultColors = hashMapOf(
            "black" to Control.COLOR_BLACK,
            "white" to Control.COLOR_WHITE,
            "red" to Control.COLOR_RED,
            "green" to Control.COLOR_GREEN,
            "blue" to Control.COLOR_BLUE,
            "magenta" to Control.COLOR_MANGENTA,
            "cyan" to Control.COLOR_CYAN,
            "yellow" to Control.COLOR_YELLOW
        )

        private const val OPS = "+*/%&|"
        private val OPS_PRIORITY = arrayOf("*/%", "+-", "&|")

        private fun findPair(list: List<VarOpPair>, ops: String): Int {
            for (i in list.indices) {
                if (ops.indexOf(list[i].op) >= 0) return i
            }
            return -1
        }

        private fun evalExpr(exprStr: String, variables: Map<String, Double>): Double {
            var expr = exprStr
            while (true) {
                var pos = expr.indexOf('(')
                if (pos < 0) break

                var end = ++pos
                var lvl = 0
                while (end < expr.length) {
                    val chr = expr[end]
                    if (chr == '(') {
                        lvl++
                    } else if (chr == ')') {
                        if (lvl == 0) break
                        lvl--
                    }
                    end++
                }

                if (end >= expr.length) {
                    throw RuntimeException("Unclosed parenthesis in expression \"$expr\"")
                }

                val valNum = evalExpr(expr.substring(pos, end), variables)
                expr = expr.substring(0, pos - 1) + valNum + expr.substring(end + 1)
            }

            val ops = ArrayList<VarOpPair>()
            val str = StringBuilder()
            var negIsPartOfStr = true
            var strIsNumber = true

            for (i in expr.indices) {
                val chr = expr[i]
                if (Character.isSpaceChar(chr)) continue

                if ((chr == '-' && !negIsPartOfStr) || OPS.indexOf(chr) >= 0) {
                    val pair = VarOpPair()
                    pair.setVar(str.toString(), strIsNumber, expr, variables)
                    pair.op = chr
                    ops.add(pair)

                    str.setLength(0)
                    negIsPartOfStr = true
                    strIsNumber = true
                } else {
                    if (strIsNumber && chr != '-' && chr != '.' && !chr.isDigit()) {
                        strIsNumber = false
                    }
                    if (negIsPartOfStr) negIsPartOfStr = false
                    str.append(chr)
                }
            }

            if (str.isNotEmpty()) {
                val pair = VarOpPair()
                pair.setVar(str.toString(), strIsNumber, expr, variables)
                pair.op = '\u0000'
                ops.add(pair)
            }

            while (true) {
                var pairId = -1
                for (opList in OPS_PRIORITY) {
                    pairId = findPair(ops, opList)
                    if (pairId >= 0) break
                }
                if (pairId < 0) break

                val a = ops[pairId]
                val b = ops[pairId + 1]
                when (a.op) {
                    '*' -> b.`var` = a.`var` * b.`var`
                    '/' -> b.`var` = a.`var` / b.`var`
                    '%' -> b.`var` = a.`var` % b.`var`
                    '+' -> b.`var` = a.`var` + b.`var`
                    '-' -> b.`var` = a.`var` - b.`var`
                    '&' -> if (a.`var` == 0.0) b.`var` = 0.0
                    '|' -> if (a.`var` != 0.0) b.`var` = a.`var`
                }

                ops.removeAt(pairId)
            }

            if (ops.size != 1 || ops[0].op != '\u0000') {
                throw RuntimeException("Error while parsing evaluating \"$expr\"")
            }

            return ops[0].`var`
        }
    }
}
