package app.lumina.sheet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import app.lumina.glass.GlassPanelView
import app.lumina.glass.LuminaDimens
import com.duckduckgo.app.browser.R
import kotlin.math.abs
import kotlin.math.max

/**
 * The one sheet in Lumina. Every panel — AI, shields, settings, downloads — is a [LuminaSheet]
 * with different content, so the drag, the spring, the scrim and the corner radius can never
 * drift between screens.
 *
 * It is a View like everything else here: no DialogFragment, no BottomSheetDialog, no Compose.
 * That keeps it inside the browser's window (so the omnibar, keyboard and back handling keep
 * working) and keeps the APK free of another dependency.
 */
class LuminaSheet @JvmOverloads constructor(
    context: Context,
    private val titleText: String,
    private val onClosed: (() -> Unit)? = null,
) : FrameLayout(context) {

    private val scrim = View(context).apply {
        setBackgroundColor(0xB3000000.toInt())
        alpha = 0f
        setOnClickListener { dismiss() }
    }

    private val panel = GlassPanelView(context).apply {
        radius = LuminaDimens.px(context, LuminaDimens.RADIUS_SHEET)
        tint = 0x1AFFFFFF
        blurStrength = LuminaDimens.BLUR_BAR
        rimEnabled = true
    }

    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(4), dp(20), dp(28))
    }

    private val header: View
    private var shown = false
    private var dragOffset = 0f

    init {
        clipChildren = false
        setWillNotDraw(false)

        addView(scrim, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        header = buildHeader()
        inner.addView(header)
        inner.addView(body, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))

        panel.addView(inner, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))

        val panelParams = android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(10)
            rightMargin = dp(10)
            bottomMargin = dp(10)
        }
        addView(panel, panelParams)

        // drag the header to dismiss; the body keeps its own clicks
        header.setOnTouchListener { _, event -> handleDrag(event) }

        visibility = GONE
    }

    private fun buildHeader(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(12), dp(10))
        }

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(
            TextView(context).apply {
                text = titleText
                setTextColor(LuminaDimens.TEXT_1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setPadding(dp(20), dp(10), 0, dp(4))
            },
            LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        row.addView(column, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val close = FrameLayout(context).apply {
            setOnClickListener { dismiss() }
            isClickable = true
            addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x22FFFFFF)
                    cornerRadius = dp(18).toFloat()
                }
            }, android.widget.FrameLayout.LayoutParams(dp(36), dp(36)))
            addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.lumina_ic_menu)
                setColorFilter(LuminaDimens.TEXT_2)
                rotation = 45f
                scaleX = 0.7f
                scaleY = 0.7f
            }, android.widget.FrameLayout.LayoutParams(dp(36), dp(36)))
        }
        row.addView(close, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(8) })

        // the grabber sits above the row, so wrap both
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrap.addView(
            FrameLayout(context).apply {
                addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x33FFFFFF)
                        cornerRadius = dp(2).toFloat()
                    }
                }, android.widget.FrameLayout.LayoutParams(dp(38), dp(4)).apply { gravity = Gravity.CENTER })
            },
            LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(18)),
        )
        wrap.addView(row)
        return wrap
    }

    /** Adds a view to the scrollable body. */
    fun addContent(view: View) {
        body.addView(view, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun addContent(view: View, topMargin: Int) {
        body.addView(view, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = dp(topMargin)
        })
    }

    fun show(parent: ViewGroup) {
        if (shown) return
        shown = true
        if (this.parent !== parent) {
            (this.parent as? ViewGroup)?.removeView(this)
            parent.addView(this, ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        }
        visibility = VISIBLE
        alpha = 0f
        panel.translationY = dp(320).toFloat()
        animate().alpha(1f).setDuration(160).start()
        panel.animate()
            .translationY(0f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
        scrim.animate().alpha(1f).setDuration(200).start()
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        panel.animate()
            .translationY(panel.height.toFloat().coerceAtLeast(dp(320).toFloat()))
            .setDuration(200)
            .start()
        scrim.animate().alpha(0f).setDuration(180).start()
        animate().alpha(0f).setDuration(220).withEndAction {
            visibility = GONE
            onClosed?.invoke()
        }.start()
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragOffset = event.rawY - panel.top
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = (event.rawY - dragOffset) - 0f
                if (delta > 0) panel.translationY = delta
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (abs(panel.translationY) > dp(110)) {
                    dismiss()
                } else {
                    panel.animate().translationY(0f).setDuration(180).start()
                }
                return true
            }
        }
        return false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /* --------------------------------------------------------------------------------------- */
    /* Building blocks shared by every sheet                                                   */
    /* --------------------------------------------------------------------------------------- */

    companion object {

        fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        /** A glass row: icon, title, subtitle, optional trailing chevron. */
        fun row(
            context: Context,
            iconRes: Int?,
            title: String,
            subtitle: String? = null,
            accent: Boolean = false,
            onClick: (() -> Unit)? = null,
        ): View {
            val panel = GlassPanelView(context).apply {
                radius = LuminaDimens.px(context, LuminaDimens.RADIUS_TILE)
                tint = 0x12FFFFFF
                blurStrength = LuminaDimens.BLUR_CARD
                setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (iconRes != null) {
                val iconHolder = FrameLayout(context)
                iconHolder.addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (accent) 0x33FF4A3D else 0x1FFFFFFF)
                        cornerRadius = dp(context, 13).toFloat()
                    }
                }, android.widget.FrameLayout.LayoutParams(dp(context, 38), dp(context, 38)))
                iconHolder.addView(android.widget.ImageView(context).apply {
                    setImageResource(iconRes)
                    setColorFilter(if (accent) LuminaDimens.ACCENT else LuminaDimens.TEXT_1)
                }, android.widget.FrameLayout.LayoutParams(dp(context, 38), dp(context, 38)))
                row.addView(iconHolder, LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)).apply {
                    rightMargin = dp(context, 12)
                })
            }
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            column.addView(TextView(context).apply {
                text = title
                setTextColor(LuminaDimens.TEXT_1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            })
            if (!subtitle.isNullOrEmpty()) {
                column.addView(TextView(context).apply {
                    text = subtitle
                    setTextColor(LuminaDimens.TEXT_3)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    setLineSpacing(dp(context, 2).toFloat(), 1f)
                }, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(context, 3)
                })
            }
            row.addView(column, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (onClick != null) {
                row.addView(TextView(context).apply {
                    text = "›"
                    setTextColor(LuminaDimens.TEXT_3)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setPadding(dp(context, 8), 0, 0, 0)
                })
            }
            panel.addView(row)
            if (onClick != null) {
                panel.isClickable = true
                panel.setOnClickListener {
                    panel.squish()
                    onClick()
                }
            }
            return panel
        }

        /** A glass action button, full width or inline. */
        fun button(context: Context, label: String, accent: Boolean = false, onClick: () -> Unit): View {
            val text = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(if (accent) 0xFF1A0B08.toInt() else LuminaDimens.TEXT_1)
                setPadding(dp(context, 18), dp(context, 13), dp(context, 18), dp(context, 13))
            }
            val panel = GlassPanelView(context).apply {
                radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
                tint = if (accent) 0xE6FF4A3D.toInt() else 0x1FFFFFFF
                blurStrength = LuminaDimens.BLUR_CARD
                addView(text)
                isClickable = true
                setOnClickListener {
                    squish()
                    onClick()
                }
            }
            return panel
        }

        /** A small stat pill, used by the shields and downloads sheets. */
        fun stat(context: Context, value: String, label: String): View {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(context, 10), dp(context, 12), dp(context, 10), dp(context, 12))
            }
            column.addView(TextView(context).apply {
                text = value
                setTextColor(LuminaDimens.TEXT_1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
            })
            column.addView(TextView(context).apply {
                text = label
                setTextColor(LuminaDimens.TEXT_3)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 3), 0, 0)
            })
            val panel = GlassPanelView(context).apply {
                radius = LuminaDimens.px(context, LuminaDimens.RADIUS_TILE)
                tint = 0x10FFFFFF
                blurStrength = LuminaDimens.BLUR_CARD
                addView(column)
            }
            return panel
        }

        /** Section label inside a sheet. */
        fun label(context: Context, text: String): View = TextView(context).apply {
            this.text = text.uppercase()
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            letterSpacing = 0.12f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(dp(context, 4), dp(context, 16), 0, dp(context, 8))
        }

        /** Empty state: an icon, a line, and an offer to do something about it. */
        fun empty(context: Context, iconRes: Int, title: String, bodyText: String): View {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 26), 0, dp(context, 26))
            }
            val iconHolder = FrameLayout(context)
            iconHolder.addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x1FFFFFFF)
                    cornerRadius = dp(context, 26).toFloat()
                }
            }, android.widget.FrameLayout.LayoutParams(dp(context, 52), dp(context, 52)))
            iconHolder.addView(android.widget.ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(LuminaDimens.TEXT_2)
                scaleX = 0.85f
                scaleY = 0.85f
            }, android.widget.FrameLayout.LayoutParams(dp(context, 52), dp(context, 52)))
            column.addView(iconHolder, LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)))
            column.addView(TextView(context).apply {
                text = title
                setTextColor(LuminaDimens.TEXT_1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 14), 0, 0)
            })
            column.addView(TextView(context).apply {
                text = bodyText
                setTextColor(LuminaDimens.TEXT_3)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setLineSpacing(dp(context, 2).toFloat(), 1f)
                setPadding(dp(context, 20), dp(context, 6), dp(context, 20), 0)
            })
            return column
        }

        /** The dimmer behind a sheet, exposed for sheets that live outside [LuminaSheet]. */
        fun scrimColor(): Int = 0xB3000000.toInt()
    }
}

/** A view that draws the sheet's grabber-friendly top notch, used by pre-L sheets. */
internal class GrabberView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }

    override fun onDraw(canvas: Canvas) {
        val w = dp(38)
        val h = dp(4)
        canvas.drawRoundRect(
            (width - w) / 2f,
            (height - h) / 2f,
            (width + w) / 2f,
            (height + h) / 2f,
            h / 2f,
            h / 2f,
            paint,
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
