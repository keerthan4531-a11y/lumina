package app.lumina.glass

import android.content.Context

/**
 * The few choices Lumina actually owns, persisted in one place: how strong the frost is, whether
 * the floating bar shows, and the theme. The settings sheet writes here; every view reads here.
 *
 * Deliberately tiny — the browser's own settings stay DuckDuckGo's, so there is exactly one
 * preferences system per app.
 */
object LuminaGlassSettings {

    private const val PREFS = "lumina_prefs"
    private const val KEY_BLUR = "blur_divisor"
    private const val KEY_BAR = "bottom_bar"
    private const val KEY_THEME = "theme"

    /** Wallpaper downscale factor. Bigger = softer frost. */
    var blurDivisor: Float = 14f
        private set

    var showBottomBar: Boolean = true
        private set

    /** "system" · "dark" · "light" — light is designed but not switched on yet. */
    var theme: String = "system"
        private set

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        blurDivisor = prefs.getFloat(KEY_BLUR, 14f)
        showBottomBar = prefs.getBoolean(KEY_BAR, true)
        theme = prefs.getString(KEY_THEME, "system") ?: "system"
    }

    fun setBlurDivisor(context: Context, value: Float) {
        blurDivisor = value
        persist(context).edit().putFloat(KEY_BLUR, value).apply()
        LuminaWallpaper.invalidate()
    }

    fun setShowBottomBar(context: Context, value: Boolean) {
        showBottomBar = value
        persist(context).edit().putBoolean(KEY_BAR, value).apply()
    }

    fun setTheme(context: Context, value: String) {
        theme = value
        persist(context).edit().putString(KEY_THEME, value).apply()
    }

    /** Soft · Standard · Strong, as the design spec calls them. */
    val presets: List<Pair<String, Float>> = listOf(
        "Soft" to 9f,
        "Standard" to 14f,
        "Strong" to 22f,
    )

    private fun persist(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
