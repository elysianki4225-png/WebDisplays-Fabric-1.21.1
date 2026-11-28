package com.terrapaver.client.gui

data class M3Palette(
    val id: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val tertiaryContainer: Int,
    val surface: Int,
    val surfaceLow: Int,
    val surfaceContainer: Int,
    val surfaceHigh: Int,
    val card: Int,
    val outline: Int,
    val outlineVariant: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val error: Int,
    val errorContainer: Int,
    val onErrorContainer: Int
)

object M3Theme {
    val THEMES = listOf(
        // 0: Google Blue M3 Light
        M3Palette(
            id = 0,
            primary = 0xFF0B57D0.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFFD3E3FD.toInt(),
            onPrimaryContainer = 0xFF041E49.toInt(),
            secondaryContainer = 0xFFC2E7FF.toInt(),
            onSecondaryContainer = 0xFF001D35.toInt(),
            tertiary = 0xFF146C2E.toInt(),
            tertiaryContainer = 0xFFC4EED0.toInt(),
            surface = 0xFFF8FAFD.toInt(),
            surfaceLow = 0xFFF3F6FC.toInt(),
            surfaceContainer = 0xFFEDF2FA.toInt(),
            surfaceHigh = 0xFFE1E7F0.toInt(),
            card = 0xFFFFFFFF.toInt(),
            outline = 0xFF747775.toInt(),
            outlineVariant = 0xFFC4C7C5.toInt(),
            onSurface = 0xFF1F1F1F.toInt(),
            onSurfaceVariant = 0xFF444746.toInt(),
            error = 0xFFB3261E.toInt(),
            errorContainer = 0xFFF9DEDC.toInt(),
            onErrorContainer = 0xFF410E0B.toInt()
        ),
        // 1: Material You Purple Light
        M3Palette(
            id = 1,
            primary = 0xFF6750A4.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFFEADDFF.toInt(),
            onPrimaryContainer = 0xFF21005D.toInt(),
            secondaryContainer = 0xFFE8DEF8.toInt(),
            onSecondaryContainer = 0xFF1D192B.toInt(),
            tertiary = 0xFF7D5260.toInt(),
            tertiaryContainer = 0xFFFFD8E4.toInt(),
            surface = 0xFFFEF7FF.toInt(),
            surfaceLow = 0xFFF7F2FA.toInt(),
            surfaceContainer = 0xFFF3EDF7.toInt(),
            surfaceHigh = 0xFFECE6F0.toInt(),
            card = 0xFFFFFFFF.toInt(),
            outline = 0xFF79747E.toInt(),
            outlineVariant = 0xFFCAC4D0.toInt(),
            onSurface = 0xFF1D1B20.toInt(),
            onSurfaceVariant = 0xFF49454F.toInt(),
            error = 0xFFB3261E.toInt(),
            errorContainer = 0xFFF9DEDC.toInt(),
            onErrorContainer = 0xFF410E0B.toInt()
        ),
        // 2: Emerald Teal Light
        M3Palette(
            id = 2,
            primary = 0xFF006A60.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFF9EF2E4.toInt(),
            onPrimaryContainer = 0xFF00201C.toInt(),
            secondaryContainer = 0xFFCCE8E2.toInt(),
            onSecondaryContainer = 0xFF05201C.toInt(),
            tertiary = 0xFF456179.toInt(),
            tertiaryContainer = 0xFFCCE5FF.toInt(),
            surface = 0xFFF4FBF9.toInt(),
            surfaceLow = 0xFFEFF5F3.toInt(),
            surfaceContainer = 0xFFE9EFED.toInt(),
            surfaceHigh = 0xFFE3EAE8.toInt(),
            card = 0xFFFFFFFF.toInt(),
            outline = 0xFF6F7977.toInt(),
            outlineVariant = 0xFFBEC9C6.toInt(),
            onSurface = 0xFF161D1C.toInt(),
            onSurfaceVariant = 0xFF3F4947.toInt(),
            error = 0xFFBA1A1A.toInt(),
            errorContainer = 0xFFFFDAD6.toInt(),
            onErrorContainer = 0xFF410002.toInt()
        ),
        // 3: Terracotta Amber Light
        M3Palette(
            id = 3,
            primary = 0xFF984716.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFFFFDBCA.toInt(),
            onPrimaryContainer = 0xFF341100.toInt(),
            secondaryContainer = 0xFFF5DED3.toInt(),
            onSecondaryContainer = 0xFF2B160C.toInt(),
            tertiary = 0xFF655F31.toInt(),
            tertiaryContainer = 0xFFECE4AA.toInt(),
            surface = 0xFFFFF8F5.toInt(),
            surfaceLow = 0xFFFDF1EC.toInt(),
            surfaceContainer = 0xFFF7EBE5.toInt(),
            surfaceHigh = 0xFFF1E5DF.toInt(),
            card = 0xFFFFFFFF.toInt(),
            outline = 0xFF85736B.toInt(),
            outlineVariant = 0xFFD7C2B9.toInt(),
            onSurface = 0xFF221A16.toInt(),
            onSurfaceVariant = 0xFF53433D.toInt(),
            error = 0xFFBA1A1A.toInt(),
            errorContainer = 0xFFFFDAD6.toInt(),
            onErrorContainer = 0xFF410002.toInt()
        )
    )

    fun get(id: Int): M3Palette = THEMES[id.coerceIn(0, THEMES.lastIndex)]

    fun withAlpha(argb: Int, alpha: Int): Int = ((alpha.coerceIn(0, 255)) shl 24) or (argb and 0x00FFFFFF)

    fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val aa = (a ushr 24) and 0xFF
        val ar = (a ushr 16) and 0xFF
        val ag = (a ushr 8) and 0xFF
        val ab = a and 0xFF

        val ba = (b ushr 24) and 0xFF
        val br = (b ushr 16) and 0xFF
        val bg = (b ushr 8) and 0xFF
        val bb = b and 0xFF

        val oa = (aa + (ba - aa) * k).toInt()
        val or = (ar + (br - ar) * k).toInt()
        val og = (ag + (bg - ag) * k).toInt()
        val ob = (ab + (bb - ab) * k).toInt()
        return (oa shl 24) or (or shl 16) or (og shl 8) or ob
    }
}
