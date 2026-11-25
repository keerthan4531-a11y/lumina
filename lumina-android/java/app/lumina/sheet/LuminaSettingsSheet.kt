package app.lumina.sheet

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.lumina.glass.GlassPanelView
import app.lumina.glass.LuminaDimens
import app.lumina.glass.LuminaGlassSettings
import com.duckduckgo.app.browser.R

/**
 * Lumina's own settings: the few things this UI owns. Everything else stays where DuckDuckGo put
 * it, so there is no second settings screen pretending to control the engine.
 */
object LuminaSettingsSheet {

    fun create(
        context: Context,
        onChanged: () -> Unit,
        onOpenBrowserSettings: () -> Unit,
        onAbout: () -> Unit,
        onClose: () -> Unit,
    ): LuminaSheet {
        LuminaGlassSettings.ensureLoaded(context)
        val sheet = LuminaSheet(context, "Settings", onClosed = onClose)

        /* ---- glass strength ------------------------------------------------------------- */
        sheet.addContent(LuminaSheet.label(context, "Glass"))
        val presetsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        LuminaGlassSettings.presets.forEach { (label, divisor) ->
            val chip = presetChip(context, label, LuminaGlassSettings.blurDivisor == divisor) {
                LuminaGlassSettings.setBlurDivisor(context, divisor)
                onChanged()
                sheet.dismiss()
            }
            presetsRow.addView(chip, LinearLayout.LayoutParams(0, dp(context, 40), 1f).apply {
                rightMargin = dp(context, 8)
            })
        }
        sheet.addContent(presetsRow)

        /* ---- bottom bar ----------------------------------------------------------------- */
        sheet.addContent(LuminaSheet.label(context, "Navigation"))
        sheet.addContent(
            toggleRow(
                context,
                R.drawable.lumina_ic_tabs,
                "Floating bottom bar",
                "Show the glass bar over the home page",
                LuminaGlassSettings.showBottomBar,
            ) { enabled ->
                LuminaGlassSettings.setShowBottomBar(context, enabled)
                onChanged()
            },
        )

        /* ---- theme ---------------------------------------------------------------------- */
        sheet.addContent(LuminaSheet.label(context, "Appearance"))
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_home,
                "Theme",
                when (LuminaGlassSettings.theme) {
                    "light" -> "Light"
                    "dark" -> "Dark"
                    else -> "Follow the system (dark glass)"
                },
            ),
        )
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_ai,
                "Wallpaper",
                "Aurora, drawn on the device. Nothing is downloaded.",
            ),
            topMargin = 8,
        )

        /* ---- about ---------------------------------------------------------------------- */
        sheet.addContent(LuminaSheet.label(context, "About"))
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_shield,
                "Lumina",
                "DuckDuckGo engine · glass UI · no analytics",
                onClick = onAbout,
            ),
        )
        sheet.addContent(
            LuminaSheet.button(context, "DuckDuckGo settings") {
                sheet.dismiss()
                onOpenBrowserSettings()
            },
            topMargin = 14,
        )

        return sheet
    }

    private fun presetChip(context: Context, label: String, active: Boolean, onClick: () -> Unit): View {
        val text = TextView(context).apply {
            text = label
            setTextColor(if (active) 0xFF1A0B08.toInt() else LuminaDimens.TEXT_2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(
                if (active) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL,
            )
            gravity = Gravity.CENTER
        }
        return GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
            tint = if (active) 0xE6FF4A3D.toInt() else 0x1AFFFFFF
            blurStrength = LuminaDimens.BLUR_CARD
            addView(text, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            isClickable = true
            setOnClickListener {
                squish()
                onClick()
            }
        }
    }

    private fun toggleRow(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        initial: Boolean,
        onToggle: (Boolean) -> Unit,
    ): View {
        var enabled = initial
        val track = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
            tint = if (enabled) 0xE6FF4A3D.toInt() else 0x33FFFFFF
            blurStrength = LuminaDimens.BLUR_CARD
        }
        val knob = View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(context, 11).toFloat()
            }
        }
        track.addView(knob, FrameLayout.LayoutParams(dp(context, 22), dp(context, 22)).apply {
            gravity = Gravity.CENTER_VERTICAL
            leftMargin = dp(context, 4)
        })

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
        }

        val icon = android.widget.ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(LuminaDimens.TEXT_1)
        }
        val iconHolder = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, 12f)
            tint = 0x14FFFFFF
            addView(icon, FrameLayout.LayoutParams(dp(context, 34), dp(context, 34)))
        }
        row.addView(iconHolder, LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)).apply {
            gap(context)
        })

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(context).apply {
            text = title
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        column.addView(TextView(context).apply {
            text = subtitle
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }, LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(context, 3) })
        row.addView(column, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(track, LinearLayout.LayoutParams(dp(context, 46), dp(context, 28)))

        val panel = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_TILE)
            tint = 0x12FFFFFF
            blurStrength = LuminaDimens.BLUR_CARD
            addView(row)
            isClickable = true
            setOnClickListener {
                enabled = !enabled
                track.tint = if (enabled) 0xE6FF4A3D.toInt() else 0x33FFFFFF
                knob.animate()
                    .translationX(if (enabled) dp(context, 18).toFloat() else 0f)
                    .setDuration(160)
                    .start()
                onToggle(enabled)
            }
        }
        knob.translationX = if (enabled) dp(context, 18).toFloat() else 0f
        return panel
    }

    private fun LinearLayout.LayoutParams.gap(context: Context) {
        rightMargin = dp(context, 12)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
