package app.lumina.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import app.lumina.glass.GlassPanelView
import app.lumina.glass.LuminaDimens
import kotlin.math.max

/**
 * The floating glass bar: one capsule, four icons, and a soft glow that slides to whichever tab is
 * selected (the thing that makes MeeTube's bar feel alive). Built in code so it cannot drift from
 * the glass material.
 */
class LuminaBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    data class Item(val iconRes: Int, val label: String)

    var items: List<Item> = emptyList()
        set(value) {
            field = value
            rebuild()
        }

    var onSelect: ((Int) -> Unit)? = null

    private var selected = 0
    private val glow = GlowView(context)
    private val icons = ArrayList<ImageView>()
    private val glowAnimator: ValueAnimator by lazy {
        ValueAnimator().apply {
            interpolator = DecelerateInterpolator(1.8f)
            duration = 260
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                glow.x = value
                glow.invalidate()
            }
        }
    }

    private val panel = GlassPanelView(context).apply {
        radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
        tint = 0x1FFFFFFF
        blurStrength = LuminaDimens.BLUR_BAR
        rimEnabled = true
        elevation = LuminaDimens.px(context, 18f)
    }

    init {
        clipChildren = false
        clipToPadding = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (icons.isNotEmpty()) {
            moveGlowTo(selected, animate = false)
        }
    }

    private fun rebuild() {
        removeAllViews()
        icons.clear()
        if (items.isEmpty()) return

        panel.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(panel)

        glow.layoutParams = LayoutParams(itemWidth(), LayoutParams.MATCH_PARENT)
        addView(glow)

        val row = FrameLayout(context)
        row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(row)

        items.forEachIndexed { index, item ->
            val icon = ImageView(context).apply {
                setImageResource(item.iconRes)
                contentDescription = item.label
                setColorFilter(LuminaDimens.TEXT_3)
                val lp = LayoutParams(itemWidth(), LayoutParams.MATCH_PARENT)
                lp.leftMargin = (itemWidth() * index).toInt()
                layoutParams = lp
                scaleType = ImageView.ScaleType.CENTER
                setPadding(0, 0, 0, 0)
                setOnClickListener { select(index) }
            }
            row.addView(icon)
            icons.add(icon)
        }

        post { moveGlowTo(selected, animate = false) }
    }

    private fun itemWidth(): Int {
        val w = max(width, resources.displayMetrics.widthPixels - dp(64))
        return w / max(1, items.size)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun select(index: Int) {
        if (index !in items.indices) return
        selected = index
        moveGlowTo(index, animate = true)
        onSelect?.invoke(index)
    }

    private fun moveGlowTo(index: Int, animate: Boolean) {
        val target = (itemWidth() * index).toFloat()
        if (animate) {
            glowAnimator.cancel()
            glowAnimator.setFloatValues(glow.x, target)
            glowAnimator.start()
        } else {
            glow.x = target
        }
        icons.forEachIndexed { i, icon ->
            val selectedColor = if (i == index) LuminaDimens.TEXT_1 else LuminaDimens.TEXT_3
            icon.setColorFilter(selectedColor)
            icon.animate()
                .scaleX(if (i == index) 1f else 0.92f)
                .scaleY(if (i == index) 1f else 0.92f)
                .setDuration(180)
                .start()
        }
    }

    /** The travelling light. A single radial gradient, no layers, no blur pass. */
    private class GlowView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = max(width, 1).toFloat() * 0.52f
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(0x59FF4A3D, 0x33FF4A3D, 0x00FF4A3D),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_SEARCH = 1
        const val TAB_TABS = 2
        const val TAB_MENU = 3
    }
}

/** A tiny glass pill holding an icon: used by the header for search and settings. */
class LuminaIconButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val panel = GlassPanelView(context).apply {
        radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
        tint = 0x1AFFFFFF
        blurStrength = LuminaDimens.BLUR_CARD
    }
    private val icon = ImageView(context).apply {
        setColorFilter(LuminaDimens.TEXT_1)
        scaleType = ImageView.ScaleType.CENTER
    }

    init {
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val size = (20 * resources.displayMetrics.density).toInt()
        val iconParams = LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        addView(icon, iconParams)
        isClickable = true
        isFocusable = true
    }

    /** Press feedback the caller can trigger without stealing its own click listener. */
    fun squish() {
        panel.squish()
    }

    fun setIcon(res: Int) {
        icon.setImageResource(res)
    }
}
