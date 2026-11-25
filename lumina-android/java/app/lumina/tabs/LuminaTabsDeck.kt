package app.lumina.tabs

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lumina.glass.GlassPanelView
import app.lumina.glass.LuminaDimens
import app.lumina.glass.LuminaWallpaperView
import com.duckduckgo.app.browser.R

/**
 * The tab deck: every open tab as a glass card, two per row, with a "new tab" tile and an honest
 * empty state. It is a View overlay rather than a new Activity so the browser's own tab model
 * stays the only source of truth — the host passes in whatever tabs exist.
 */
class LuminaTabsDeck @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : FrameLayout(context, attrs) {

    data class Tab(val title: String, val url: String, val selected: Boolean)

    var onOpenTab: ((Tab) -> Unit)? = null
    var onCloseTab: ((Tab) -> Unit)? = null
    var onNewTab: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val grid = LinearLayout(context)
    private val counter = TextView(context)

    init {
        visibility = GONE
        setWillNotDraw(false)
        addView(LuminaWallpaperView(context), android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        addView(buildHeader(), android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(dp(16), dp(14), dp(16), dp(120))
        scroll.addView(grid, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT).apply { topMargin = dp(64) })

        val close = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, 28f)
            tint = 0x1FFFFFFF
            blurStrength = LuminaDimens.BLUR_BAR
        }
        close.addView(TextView(context).apply {
            text = "Done"
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }, android.widget.FrameLayout.LayoutParams(dp(84), dp(44)))
        close.setOnClickListener { hide() }
        addView(close, android.widget.FrameLayout.LayoutParams(dp(84), dp(44)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(30)
        })
    }

    private fun buildHeader(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(6))
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(context).apply {
            text = "Tabs"
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.02f
        })
        counter.apply {
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        column.addView(counter)
        row.addView(column, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val add = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, 20f)
            tint = 0x1FFFFFFF
            blurStrength = LuminaDimens.BLUR_BAR
            addView(ImageView(context).apply {
                setImageResource(R.drawable.lumina_ic_plus)
                setColorFilter(LuminaDimens.TEXT_1)
            }, android.widget.FrameLayout.LayoutParams(dp(20), dp(20)).apply { gravity = Gravity.CENTER })
            isClickable = true
            setOnClickListener {
                squish()
                onNewTab?.invoke()
            }
        }
        row.addView(add, LinearLayout.LayoutParams(dp(42), dp(42)))
        return row
    }

    fun render(tabs: List<Tab>) {
        grid.removeAllViews()
        counter.text = when (tabs.size) {
            0 -> "No tabs yet"
            1 -> "1 tab open"
            else -> "${tabs.size} tabs open"
        }
        if (tabs.isEmpty()) {
            grid.addView(LuminaTabsDeck.emptyState(context, onNewTab))
            return
        }
        tabs.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            pair.forEach { tab ->
                row.addView(card(tab), LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dp(10)
                })
            }
            if (pair.size == 1) {
                row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            }
            grid.addView(row, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            })
        }
    }

    private fun card(tab: Tab): View {
        val panel = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_CARD)
            tint = if (tab.selected) 0x26FFFFFF else 0x14FFFFFF
            blurStrength = LuminaDimens.BLUR_BAR
            setPadding(dp(14), dp(12), dp(10), dp(12))
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ImageView(context).apply {
            setImageResource(R.drawable.lumina_ic_globe)
            setColorFilter(if (tab.selected) LuminaDimens.ACCENT else LuminaDimens.TEXT_3)
        }, LinearLayout.LayoutParams(dp(16), dp(16)))
        top.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        val close = TextView(context).apply {
            text = "✕"
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                onCloseTab?.invoke(tab)
            }
        }
        top.addView(close)
        column.addView(top)

        column.addView(TextView(context).apply {
            text = tab.title.ifBlank { "New tab" }
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        })
        column.addView(TextView(context).apply {
            text = tab.url
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        })

        panel.addView(column)
        panel.isClickable = true
        panel.setOnClickListener {
            panel.squish()
            onOpenTab?.invoke(tab)
        }
        return panel
    }

    fun show(parent: android.view.ViewGroup) {
        if (parentView !== parent) {
            (parentView as? android.view.ViewGroup)?.removeView(this)
            parent.addView(this, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        }
        visibility = VISIBLE
        alpha = 0f
        translationY = dp(24).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    fun hide() {
        animate().alpha(0f).translationY(dp(24).toFloat()).setDuration(180).withEndAction {
            visibility = GONE
            onDismiss?.invoke()
        }.start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private fun emptyState(context: Context, onNewTab: (() -> Unit)?): View {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 60), 0, 0)
            }
            column.addView(ImageView(context).apply {
                setImageResource(R.drawable.lumina_ic_tabs)
                setColorFilter(LuminaDimens.TEXT_3)
            }, LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)))
            column.addView(TextView(context).apply {
                text = "No tabs open"
                setTextColor(LuminaDimens.TEXT_1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 12), 0, 0)
            })
            column.addView(TextView(context).apply {
                text = "Open something and it will show up here."
                setTextColor(LuminaDimens.TEXT_3)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
            })
            if (onNewTab != null) {
                val button = GlassPanelView(context).apply {
                    radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
                    tint = 0xE6FF4A3D.toInt()
                    addView(TextView(context).apply {
                        text = "New tab"
                        setTextColor(0xFF1A0B08.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        gravity = Gravity.CENTER
                        setPadding(dp(context, 22), dp(context, 11), dp(context, 22), dp(context, 11))
                    })
                    isClickable = true
                    setOnClickListener {
                        squish()
                        onNewTab.invoke()
                    }
                }
                column.addView(button, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(context, 18)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
            }
            return column
        }

        private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}
