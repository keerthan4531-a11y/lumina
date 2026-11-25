package app.lumina.sheet

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.lumina.glass.GlassPanelView
import app.lumina.glass.LuminaDimens
import com.duckduckgo.app.browser.R

/**
 * Privacy, told straight: Lumina does not run its own tracker counters, because the blocking is
 * done by the DuckDuckGo engine underneath. So this sheet explains what is already on and hands
 * the switches that exist over to the browser's own settings — it never invents a number.
 */
object LuminaPrivacySheet {

    fun create(
        context: Context,
        onOpenBrowserSettings: () -> Unit,
        onClose: () -> Unit,
    ): LuminaSheet {
        val sheet = LuminaSheet(context, "Privacy", onClosed = onClose)

        val hero = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 10), 0, dp(context, 4))
        }

        val badge = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, 34f)
            tint = 0x2EFF4A3D
            blurStrength = LuminaDimens.BLUR_BAR
        }
        val badgeInner = android.widget.FrameLayout(context)
        badgeInner.addView(android.widget.ImageView(context).apply {
            setImageResource(R.drawable.lumina_ic_shield)
            setColorFilter(LuminaDimens.ACCENT)
        }, android.widget.FrameLayout.LayoutParams(dp(context, 68), dp(context, 68)).apply {
            gravity = Gravity.CENTER
        })
        badge.addView(badgeInner, android.widget.FrameLayout.LayoutParams(dp(context, 68), dp(context, 68)))
        hero.addView(badge, LinearLayout.LayoutParams(dp(context, 68), dp(context, 68)))

        hero.addView(TextView(context).apply {
            text = "Blocking is on"
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 12), 0, 0)
        })
        hero.addView(TextView(context).apply {
            text = "Trackers, cookie prompts and fingerprinting are handled by the DuckDuckGo engine, before a page renders. Lumina does not phone home about any of it."
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setLineSpacing(dp(context, 2).toFloat(), 1f)
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), 0)
        })

        sheet.addContent(hero)

        sheet.addContent(LuminaSheet.label(context, "What is protecting you"))
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_shield,
                "Tracker blocking",
                "On. Third-party trackers never get the request.",
            ),
        )
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_globe,
                "Cookie prompts",
                "Auto-handled: consent pop-ups are answered without a dialog.",
            ),
            topMargin = 8,
        )
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_lock,
                "Encryption",
                "HTTPS upgraded where the site supports it.",
            ),
            topMargin = 8,
        )
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_ai,
                "What Lumina adds",
                "No analytics SDK, no crash reporter, no ad ID. The only network calls are the ones you ask for.",
                accent = true,
            ),
            topMargin = 8,
        )

        sheet.addContent(LuminaSheet.label(context, "Fine tuning"))
        sheet.addContent(
            LuminaSheet.button(context, "Open browser privacy settings", accent = true) {
                sheet.dismiss()
                onOpenBrowserSettings()
            },
        )

        return sheet
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
