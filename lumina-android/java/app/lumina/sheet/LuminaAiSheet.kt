package app.lumina.sheet

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.lumina.glass.GlassPanelView
import app.lumina.glass.LuminaDimens
import com.duckduckgo.app.browser.R

/**
 * "Ask Lumina". A real sheet, not a mock chat: the prompt field is a working EditText, the
 * suggestion chips fill it, and the send button hands the text to whatever the browser wires up
 * (today: the DuckDuckGo AI entry point). No fake assistant messages are invented here.
 */
object LuminaAiSheet {

    fun create(
        context: Context,
        onAsk: (String) -> Unit,
        onClose: () -> Unit,
    ): LuminaSheet {
        val sheet = LuminaSheet(context, "Ask Lumina", onClosed = onClose)

        sheet.addContent(TextView(context).apply {
            text = "Summarise what you're reading, or ask about anything. Your prompt leaves the phone only when you send it."
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setLineSpacing(dp(context, 2).toFloat(), 1f)
        })

        sheet.addContent(suggestions(context) { prompt ->
            onAsk(prompt)
            sheet.dismiss()
        }, topMargin = 16)

        val field = EditText(context).apply {
            hint = "Ask anything…"
            setHintTextColor(LuminaDimens.TEXT_3)
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            maxLines = 4
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    val text = text?.toString()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        onAsk(text)
                        sheet.dismiss()
                    }
                    true
                } else {
                    false
                }
            }
        }

        val fieldPanel = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_TILE)
            tint = 0x1AFFFFFF
            blurStrength = LuminaDimens.BLUR_CARD
            addView(field, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        sheet.addContent(fieldPanel, topMargin = 16)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            TextView(context).apply {
                text = "Private by default"
                setTextColor(LuminaDimens.TEXT_3)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            },
            LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        actions.addView(
            LuminaSheet.button(context, "Send", accent = true) {
                val text = field.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    onAsk(text)
                    sheet.dismiss()
                } else {
                    field.requestFocus()
                }
            },
            LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        sheet.addContent(actions, topMargin = 14)

        sheet.addContent(LuminaSheet.label(context, "What it is good at"))
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_ai,
                "Read the page for you",
                "A summary of the article that is open, with the parts that matter first.",
                accent = true,
            ),
        )
        sheet.addContent(
            LuminaSheet.row(
                context,
                R.drawable.lumina_ic_shield,
                "Stays on the phone",
                "Nothing is stored. The prompt is sent only when you press Send.",
            ),
            topMargin = 8,
        )

        return sheet
    }

    private fun suggestions(context: Context, onPick: (String) -> Unit): View {
        val scroller = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        listOf(
            "Summarise this page",
            "Explain it simply",
            "Compare the results",
            "Translate to Tamil",
            "Find the key numbers",
        ).forEach { label ->
            val text = TextView(context).apply {
                text = label
                setTextColor(LuminaDimens.TEXT_1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9))
            }
            val chip = GlassPanelView(context).apply {
                radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
                tint = 0x1AFFFFFF
                blurStrength = LuminaDimens.BLUR_CARD
                addView(text)
                isClickable = true
                setOnClickListener {
                    squish()
                    onPick(label)
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 36)).apply {
                rightMargin = dp(context, 8)
            })
        }

        scroller.addView(row, FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroller
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
