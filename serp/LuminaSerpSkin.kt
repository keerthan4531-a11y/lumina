package app.lumina.search.serp

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.atomic.AtomicReference

/**
 * Lumina SERP skin — injects [lumina-skin.css] + [lumina-skin.js] at document_start so the page
 * never paints in its old style first (that white flash is the whole "looks 2014" complaint).
 *
 * Belongs to the `lumina-search` module (P5, layer A). Nothing here is DDG-specific: it is a
 * user-agent-side restyle of whatever SERP the user is on.
 */
class LuminaSerpSkin(private val context: Context) {

    private val css: String by lazy { context.assets.open("serp/lumina-skin.css").readBytes().decodeToString() }
    private val js: String by lazy { context.assets.open("serp/lumina-skin.js").readBytes().decodeToString() }
    private val selectorsJson: String by lazy { context.assets.open("serp/selectors.json").readBytes().decodeToString() }

    /** Compile once per WebView, not per page. */
    fun install(webView: WebView) {
        val script = buildScript(theme = "dark", motion = true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        } else {
            // API 28–30 fallback: inject on page start. Accepts one frame of FOUC.
            webView.evaluateJavascript(script, null)
        }
    }

    fun setTheme(webView: WebView, theme: String) =
        webView.evaluateJavascript("window.__luminaSkin&&window.__luminaSkin.setTheme('$theme')", null)

    fun setMotion(webView: WebView, on: Boolean) =
        webView.evaluateJavascript("window.__luminaSkin&&window.__luminaSkin.setMotion($on)", null)

    /** Pull the result list out of the DOM for the native card layer (P5, layer B). */
    fun extract(webView: WebView, onResults: (String) -> Unit) {
        webView.evaluateJavascript(
            "JSON.stringify((window.__luminaSkin&&window.__luminaSkin.extract())||[])",
        ) { json -> onResults(json ?: "[]") }
    }

    /**
     * One <style> tag, no web fonts, no network — the skin is text, never code from the network.
     * The selectors map is embedded as a JS object so the skin can fail open per key.
     */
    private fun buildScript(theme: String, motion: Boolean): String = """
        (function(){
          if (document.getElementById('lumina-skin')) return;
          window.__LUMINA_SELECTORS__ = $selectorsJson;
          var s = document.createElement('style');
          s.id = 'lumina-skin';
          s.textContent = ${css.toJsStringLiteral()};
          (document.head || document.documentElement).appendChild(s);
          document.documentElement.setAttribute('data-lumina-theme', '$theme');
          document.documentElement.setAttribute('data-lumina-motion', '${if (motion) "on" else "off"}');
        })();
        ${js}
    """.trimIndent()

    private fun String.toJsStringLiteral(): String = buildString {
        append('"')
        for (ch in this@toJsStringLiteral) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}

/** Marker held by the browser tab so the skin is installed exactly once per WebView. */
private val skinInstalled = AtomicReference<WebView?>(null)

fun WebView.installLuminaSkinOnce(skin: LuminaSerpSkin) {
    if (skinInstalled.getAndSet(this) === this) return
    skin.install(this)
}
