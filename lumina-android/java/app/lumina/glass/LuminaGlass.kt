package app.lumina.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * Lumina's glass material, in the same shape MeeTube uses: a real, static, blurred sample of the
 * backdrop plus tint, specular and a hairline rim. No fake gradients pretending to be glass.
 *
 * Everything here is a plain View, because the browser is a View app: a ComposeView on top of the
 * new-tab panel is exactly what swallowed the omnibar's search / mic / lens taps.
 */
object LuminaDimens {

    // shape
    const val RADIUS_CARD = 26f
    const val RADIUS_PILL = 100f
    const val RADIUS_SHEET = 32f
    const val RADIUS_TILE = 18f
    const val HAIRLINE = 1f

    // glass
    const val BLUR_BAR = 0.030f     // fraction of the screen width, so it scales with density
    const val BLUR_CARD = 0.018f
    const val TINT_DARK = 0x14FFFFFF
    const val TINT_CARD = 0x0DFFFFFF
    val TINT_LIGHT: Int = 0x8CFFFFFF.toInt()

    // palette (kept in code so a layout can never drift from the material)
    val WALL_0: Int = 0xFF0A0A0D.toInt()
    val WALL_1: Int = 0xFF37131F.toInt()
    val WALL_2: Int = 0xFF101A33.toInt()
    val WALL_3: Int = 0xFF3D1207.toInt()
    val ACCENT: Int = 0xFFFF4A3D.toInt()
    const val ACCENT_SOFT = 0x33FF4A3D
    val TEXT_1: Int = 0xFFF6F7F9.toInt()
    val TEXT_2: Int = 0xFFB4BAC4.toInt()
    val TEXT_3: Int = 0xFF7D838E.toInt()
    const val RIM_TOP = 0x59FFFFFF
    const val RIM_BOTTOM = 0x0DFFFFFF
    const val SPECULAR = 0x1FFFFFFF

    fun px(context: Context, dp: Float): Float = dp * context.resources.displayMetrics.density
}

/**
 * The wallpaper is drawn once, then sampled (downscaled) by every glass panel. Sampling a single
 * small bitmap is what makes a dozen frosted surfaces cost almost nothing: there is no per-frame
 * blur, no RenderEffect, no Compose layer capture.
 */
object LuminaWallpaper {

    private const val SAMPLE_DIVISOR = 14f
    private var sample: Bitmap? = null
    private var sampleWidth = 0
    private var sampleHeight = 0

    /** A soft aurora: three warm/cool pools over near-black. Painted straight onto a canvas. */
    fun paint(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()

        val base = Paint(Paint.ANTI_ALIAS_FLAG)
        base.color = LuminaDimens.WALL_0
        canvas.drawRect(0f, 0f, w, h, base)

        pool(canvas, w * 0.18f, h * 0.10f, w * 0.95f, LuminaDimens.WALL_1, 0xD9)
        pool(canvas, w * 1.02f, h * 0.34f, w * 0.85f, LuminaDimens.WALL_2, 0xCC)
        pool(canvas, w * 0.62f, h * 0.92f, w * 0.95f, LuminaDimens.WALL_3, 0xB3)
        pool(canvas, w * 0.06f, h * 0.72f, w * 0.70f, 0xFF14243F.toInt(), 0x99)

        // a faint top sheen, so the very top of the page is not a dead flat band
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
        sheen.shader = LinearGradient(
            0f, 0f, 0f, h * 0.35f,
            intArrayOf(0x1AFFFFFF, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h * 0.35f, sheen)
    }

    private fun pool(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rgb = color and 0x00FFFFFF
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf((alpha shl 24) or rgb, (alpha * 2 / 3) shl 24 or rgb, 0x00000000 or rgb),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, paint)
    }

    /** Downscaled wallpaper, built once per size. Cheap enough to sample on every panel draw. */
    @Synchronized
    fun sample(context: Context, widthPx: Int, heightPx: Int): Bitmap {
        val cached = sample
        if (cached != null && sampleWidth == widthPx && sampleHeight == heightPx) return cached

        val w = max(8, (widthPx / SAMPLE_DIVISOR).toInt())
        val h = max(8, (heightPx / SAMPLE_DIVISOR).toInt())
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(w / widthPx.toFloat(), h / heightPx.toFloat())
        paint(canvas, widthPx, heightPx)

        sample = bitmap
        sampleWidth = widthPx
        sampleHeight = heightPx
        return bitmap
    }
}

/** The page background. Static on purpose: motion belongs to the glow, not to the floor. */
class LuminaWallpaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        LuminaWallpaper.paint(canvas, width, height)
    }
}

/**
 * A frosted panel. Draws, in order: blurred backdrop slice → tint → specular → hairline rim,
 * all clipped to [radius]. Children are drawn on top, so a card is just a GlassPanelView with
 * content inside it.
 */
open class GlassPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var radius: Float = LuminaDimens.px(context, LuminaDimens.RADIUS_CARD)
        set(value) {
            field = value
            invalidate()
        }

    var tint: Int = LuminaDimens.TINT_CARD
        set(value) {
            field = value
            invalidate()
        }

    var blurStrength: Float = LuminaDimens.BLUR_CARD
        set(value) {
            field = value
            invalidate()
        }

    var rimEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val path = Path()
    private val rect = RectF()
    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specularRect = RectF()

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        clipToOutline = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (width == 0 || height == 0 || radius <= 0f) {
            super.draw(canvas)
            return
        }
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)

        val saved = canvas.save()
        canvas.clipPath(path)
        drawBackdrop(canvas)
        drawTint(canvas)
        if (rimEnabled) drawSpecular(canvas)
        canvas.restoreToCount(saved)

        // children, still clipped to the same rounded shape
        val childSave = canvas.save()
        canvas.clipPath(path)
        dispatchDraw(canvas)
        canvas.restoreToCount(childSave)

        if (rimEnabled) drawRim(canvas)
    }

    private fun drawBackdrop(canvas: Canvas) {
        val screen = context.resources.displayMetrics
        val screenW = max(screen.widthPixels, width)
        val screenH = max(screen.heightPixels, height)
        val blurred = LuminaWallpaper.sample(context, screenW, screenH)

        val scaleX = blurred.width / screenW.toFloat()
        val scaleY = blurred.height / screenH.toFloat()

        val location = IntArray(2)
        getLocationInWindow(location)
        val top = max(0, (location[1] * scaleY).toInt())
        val left = max(0, (location[0] * scaleX).toInt())
        val srcW = min(blurred.width - left, max(1, (width * scaleX).toInt()))
        val srcH = min(blurred.height - top, max(1, (height * scaleY).toInt()))
        if (srcW <= 0 || srcH <= 0) return

        val src = Rect(left, top, left + srcW, top + srcH)
        backdropPaint.alpha = 255
        canvas.drawBitmap(blurred, src, rect, backdropPaint)
    }

    private fun drawTint(canvas: Canvas) {
        tintPaint.color = tint
        canvas.drawRect(rect, tintPaint)
    }

    private fun drawSpecular(canvas: Canvas) {
        specularRect.set(0f, 0f, width.toFloat(), height * 0.85f)
        specularPaint.shader = LinearGradient(
            0f, 0f, width * 0.65f, height * 0.85f,
            intArrayOf(LuminaDimens.SPECULAR, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(specularRect, specularPaint)
    }

    private fun drawRim(canvas: Canvas) {
        rimPaint.strokeWidth = LuminaDimens.px(context, LuminaDimens.HAIRLINE) * 1.2f
        rimPaint.style = Paint.Style.STROKE
        rimPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(LuminaDimens.RIM_TOP, LuminaDimens.RIM_BOTTOM),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        rect.inset(rimPaint.strokeWidth / 2f, rimPaint.strokeWidth / 2f)
        canvas.drawRoundRect(rect, radius, radius, rimPaint)
        rect.inset(-rimPaint.strokeWidth / 2f, -rimPaint.strokeWidth / 2f)
    }

    /** The press state MeeTube uses: the whole panel squishes, never just the icon. */
    fun squish() {
        animate()
            .scaleX(0.965f)
            .scaleY(0.965f)
            .setDuration(70)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            .start()
    }

    companion object {
        /** A pill: the radius is the height, so it reads as a capsule at any size. */
        fun pill(context: Context): GlassPanelView = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
            tint = LuminaDimens.TINT_DARK
        }

        fun card(context: Context): GlassPanelView = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_CARD)
            tint = LuminaDimens.TINT_CARD
        }
    }
}

/** Small helper so Kotlin code can round a corner without a drawable per radius. */
fun View.roundBackground(color: Int, radiusPx: Float) {
    background = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }
}

/** Colour of the day for a source tile: stable per domain, never random per scroll. */
fun monogramColor(seed: String): Int {
    val palette = intArrayOf(
        0xFFE4462F.toInt(), 0xFF3F6BD8.toInt(), 0xFF2E8B6E.toInt(),
        0xFFB0462A.toInt(), 0xFF6C4BD8.toInt(), 0xFF1F7A8C.toInt(),
        0xFF9C2B4B.toInt(), 0xFF4A5A2B.toInt(),
    )
    val index = (seed.hashCode() % palette.size + palette.size) % palette.size
    return palette[index]
}

/** 0x0412 → "#0412"? Not needed; kept for symmetry with the rest of the kit. */
fun alphaOf(color: Int): Int = Color.alpha(color)
