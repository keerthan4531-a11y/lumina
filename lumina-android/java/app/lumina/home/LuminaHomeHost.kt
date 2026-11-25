package app.lumina.home

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lumina.glass.GlassPanelView
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import app.lumina.glass.LuminaDimens
import app.lumina.glass.LuminaGlassSettings
import app.lumina.glass.LuminaWallpaper
import app.lumina.sheet.LuminaAiSheet
import app.lumina.sheet.LuminaPrivacySheet
import app.lumina.sheet.LuminaSettingsSheet
import app.lumina.tabs.LuminaTabsDeck
import app.lumina.glass.LuminaWallpaperView
import app.lumina.glass.monogramColor
import com.duckduckgo.app.browser.R

/**
 * Lumina's home page, as plain Android views inside the DuckDuckGo new-tab panel.
 *
 * Why views and not Compose: the previous Compose overlay was drawn on top of the whole panel and
 * ate the touch stream, which is why the omnibar's search field, mic and lens did nothing. A
 * ViewGroup only consumes what its children consume, so the browser's own omnibar keeps working.
 *
 * What it shows is real: Google News' public RSS (topic chips switch real sections), publisher
 * favicons from DuckDuckGo's icon service, and nothing else. No hardcoded headlines.
 */
class LuminaHomeHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onOpenUrl: ((String) -> Unit)? = null
    var onSearchTap: (() -> Unit)? = null
    var onMic: (() -> Unit)? = null
    var onLens: (() -> Unit)? = null
    var onTab: ((Int) -> Unit)? = null
    var onSettings: (() -> Unit)? = null

    private val wallpaper = LuminaWallpaperView(context)
    private val scroll = ScrollView(context)
    private val content = LinearLayout(context)
    private val feed = LinearLayout(context)
    private val bottomBar = LuminaBottomBar(context)
    private val chips = LinearLayout(context)
    private val sectionTitle = TextView(context)
    private var chipViews = mutableListOf<TextView>()
    private var topic = LuminaNewsRepository.topics.first()
    private var loading = false
    private var appliedInset = -1
    private var currentSheet: app.lumina.sheet.LuminaSheet? = null
    private var tabsDeck: LuminaTabsDeck? = null
    private var knownTabs: List<LuminaTabsDeck.Tab> = emptyList()

    init {
        setWillNotDraw(false)
        clipChildren = false
        LuminaGlassSettings.ensureLoaded(context)
        buildWallpaper()
        buildScroll()
        buildBottomBar()
        bottomBar.visibility = if (LuminaGlassSettings.showBottomBar) View.VISIBLE else View.GONE
        selectTopic(topic, force = false)
    }

    /* --------------------------------------------------------------------------------------- */
    /* Construction                                                                            */
    /* --------------------------------------------------------------------------------------- */

    private fun buildWallpaper() {
        addView(wallpaper, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun buildScroll() {
        scroll.isFillViewport = true
        scroll.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), 0, dp(18), dp(120))
        scroll.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(scroll, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        content.addView(buildHeader())
        content.addView(buildChips(), marginParams(top = 14))
        content.addView(buildSectionHeader(), marginParams(top = 20))
        content.addView(feed, marginParams(top = 12))
    }

    private fun buildHeader(): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(context).apply {
            text = "Lumina"
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.03f
        }
        row.addView(title)
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val search = LuminaIconButton(context).apply {
            setIcon(R.drawable.lumina_ic_search)
            setOnClickListener { animatePress(this); onSearchTap?.invoke() }
        }
        row.addView(search, LinearLayout.LayoutParams(dp(44), dp(44)))

        val privacy = LuminaIconButton(context).apply {
            setIcon(R.drawable.lumina_ic_shield)
            setOnClickListener { animatePress(this); openPrivacy() }
        }
        row.addView(
            privacy,
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) },
        )

        val settings = LuminaIconButton(context).apply {
            setIcon(R.drawable.lumina_ic_menu)
            setOnClickListener { animatePress(this); openSettings() }
        }
        row.addView(
            settings,
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) },
        )
        return row
    }

    private fun buildChips(): View {
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        chips.orientation = LinearLayout.HORIZONTAL
        scroller.addView(chips, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        LuminaNewsRepository.topics.forEach { candidate ->
            val chip = TextView(context).apply {
                text = candidate.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(9), dp(16), dp(9))
            }
            val holder = GlassPanelView(context).apply {
                radius = LuminaDimens.px(context, LuminaDimens.RADIUS_PILL)
                tint = 0x14FFFFFF
                blurStrength = LuminaDimens.BLUR_CARD
                addView(chip, android.widget.FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
            holder.setOnClickListener { selectTopic(candidate, force = false) }
            chips.addView(
                holder,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
                    .apply { rightMargin = dp(8) },
            )
            chipViews.add(chip)
        }
        return scroller
    }

    private fun buildSectionHeader(): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        sectionTitle.apply {
            text = topic.label
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        row.addView(sectionTitle)
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val refresh = TextView(context).apply {
            text = "Refresh"
            setTextColor(LuminaDimens.TEXT_2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { load(topic, force = true) }
        }
        row.addView(refresh)
        return row
    }

    private fun buildBottomBar() {
        bottomBar.items = listOf(
            LuminaBottomBar.Item(R.drawable.lumina_ic_home, "Home"),
            LuminaBottomBar.Item(R.drawable.lumina_ic_search, "Search"),
            LuminaBottomBar.Item(R.drawable.lumina_ic_tabs, "Tabs"),
            LuminaBottomBar.Item(R.drawable.lumina_ic_ai, "Ask Lumina"),
        )
        bottomBar.onSelect = { index ->
            when (index) {
                LuminaBottomBar.TAB_HOME -> scroll.smoothScrollTo(0, 0)
                LuminaBottomBar.TAB_SEARCH -> onSearchTap?.invoke()
                LuminaBottomBar.TAB_TABS -> showTabs()
                else -> openAi()
            }
        }
        val params = android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(64)).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(20)
            rightMargin = dp(20)
            bottomMargin = dp(22)
        }
        addView(bottomBar, params)
    }

    /* --------------------------------------------------------------------------------------- */
    /* Feed                                                                                    */
    /* --------------------------------------------------------------------------------------- */

    private fun selectTopic(candidate: LuminaNewsRepository.Topic, force: Boolean) {
        topic = candidate
        sectionTitle.text = candidate.label
        chipViews.forEach { chip ->
            val active = chip.text == candidate.label
            chip.setTextColor(if (active) LuminaDimens.ACCENT else LuminaDimens.TEXT_2)
            chip.typeface = Typeface.create(
                if (active) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL,
            )
        }
        load(candidate, force)
    }

    private fun load(candidate: LuminaNewsRepository.Topic, force: Boolean) {
        if (loading) return
        val cached = if (force) null else LuminaNewsRepository.cached(candidate.key)
        if (cached != null && cached.isNotEmpty()) {
            render(cached)
            return
        }
        loading = true
        showSkeletons()
        LuminaNewsRepository.load(candidate, force = force) { items ->
            loading = false
            if (items.isEmpty()) showError() else render(items)
        }
    }

    private fun render(items: List<NewsItem>) {
        feed.removeAllViews()
        items.take(24).forEachIndexed { index, item ->
            val card = newsCard(item)
            feed.addView(card, marginParams(bottom = if (index == items.size - 1) 0 else 10))
        }
    }

    private fun newsCard(item: NewsItem): View {
        val card = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_CARD)
            tint = LuminaDimens.TINT_CARD
            blurStrength = LuminaDimens.BLUR_CARD
            setPadding(dp(12), dp(12), dp(14), dp(12))
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val tile = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_TILE)
            tint = 0x1FFFFFFF
            blurStrength = LuminaDimens.BLUR_CARD
        }
        val favicon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = View.INVISIBLE
        }
        val monogram = TextView(context).apply {
            text = item.source.take(1).uppercase()
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        tile.addView(favicon, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        tile.addView(monogram, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        row.addView(tile, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })

        LuminaFavicons.load(item.domain) { bitmap ->
            if (bitmap != null) {
                favicon.setImageBitmap(bitmap)
                favicon.visibility = View.VISIBLE
                monogram.visibility = View.GONE
            }
        }

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(context).apply {
            text = item.title
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        column.addView(title)

        val meta = TextView(context).apply {
            val time = LuminaNewsRepository.relativeTime(item.publishedAt)
            text = if (time.isEmpty()) item.source else "${item.source}  ·  $time"
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        column.addView(meta, marginParams(top = 6))
        row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(row)
        card.setOnClickListener {
            card.squish()
            onOpenUrl?.invoke(item.url)
        }
        return card
    }

    private fun showSkeletons() {
        feed.removeAllViews()
        repeat(4) { index ->
            val skeleton = GlassPanelView(context).apply {
                radius = LuminaDimens.px(context, LuminaDimens.RADIUS_CARD)
                tint = 0x0AFFFFFF
                blurStrength = LuminaDimens.BLUR_CARD
            }
            val bars = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(16), dp(14), dp(16))
            }
            bars.addView(skeletonBar(0.92f, 14))
            bars.addView(skeletonBar(0.72f, 14), marginParams(top = 8))
            bars.addView(skeletonBar(0.35f, 11), marginParams(top = 12))
            skeleton.addView(bars)
            feed.addView(skeleton, marginParams(bottom = if (index == 3) 0 else 10))
        }
    }

    private fun skeletonBar(widthFraction: Float, heightDp: Int): View {
        val bar = View(context)
        bar.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x14FFFFFF)
            cornerRadius = dp(heightDp / 2f).toFloat()
        }
        bar.layoutParams = LinearLayout.LayoutParams(0, dp(heightDp.toFloat())).apply {
            weight = widthFraction
        }
        // weight only fills part of the row; wrap it so the remainder stays empty
        val holder = LinearLayout(context)
        holder.orientation = LinearLayout.HORIZONTAL
        holder.addView(bar)
        holder.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f - widthFraction))
        return holder
    }

    private fun showError() {
        feed.removeAllViews()
        val card = GlassPanelView(context).apply {
            radius = LuminaDimens.px(context, LuminaDimens.RADIUS_CARD)
            tint = LuminaDimens.TINT_CARD
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(context).apply {
            text = "Couldn't reach the news feed"
            setTextColor(LuminaDimens.TEXT_1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        column.addView(TextView(context).apply {
            text = "Check the connection — Lumina only loads the headlines you asked for."
            setTextColor(LuminaDimens.TEXT_3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setLineSpacing(dp(2).toFloat(), 1f)
        }, marginParams(top = 6))
        val retry = TextView(context).apply {
            text = "Try again"
            setTextColor(LuminaDimens.ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(12), 0, 0)
            setOnClickListener { load(topic, force = true) }
        }
        column.addView(retry)
        card.addView(column)
        feed.addView(card)
    }

    /* --------------------------------------------------------------------------------------- */
    /* Sheets                                                                                  */
    /* --------------------------------------------------------------------------------------- */

    private fun openAi() {
        val sheet = LuminaAiSheet.create(
            context,
            onAsk = { prompt -> submitToOmnibar(prompt) },
            onClose = { currentSheet = null },
        )
        currentSheet = sheet
        sheet.show(this)
    }

    private fun openPrivacy() {
        val sheet = LuminaPrivacySheet.create(
            context,
            onOpenBrowserSettings = { submitToOmnibar("duckduckgo://settings") },
            onClose = { currentSheet = null },
        )
        currentSheet = sheet
        sheet.show(this)
    }

    private fun openSettings() {
        val sheet = LuminaSettingsSheet.create(
            context,
            onChanged = { applySettings() },
            onOpenBrowserSettings = { submitToOmnibar("duckduckgo://settings") },
            onAbout = { submitToOmnibar("https://duckduckgo.com/about") },
            onClose = { currentSheet = null },
        )
        currentSheet = sheet
        sheet.show(this)
    }

    /** Settings that change what is already on screen take effect immediately. */
    private fun applySettings() {
        bottomBar.visibility = if (LuminaGlassSettings.showBottomBar) View.VISIBLE else View.GONE
        LuminaWallpaper.invalidate()
        wallpaper.invalidate()
        content.invalidate()
        feed.invalidate()
    }

    private fun showTabs() {
        val deck = tabsDeck ?: LuminaTabsDeck(context).also {
            it.onNewTab = { hideTabs(); onSearchTap?.invoke() }
            it.onDismiss = { }
            addView(it, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            tabsDeck = it
        }
        deck.render(knownTabs)
        deck.show(this)
    }

    private fun hideTabs() {
        tabsDeck?.hide()
    }

    /** The browser is the source of truth: the host fills this in when it knows the tabs. */
    fun setTabs(tabs: List<LuminaTabsDeck.Tab>) {
        knownTabs = tabs
        tabsDeck?.render(tabs)
    }

    /** Types into the browser's real address bar and submits, so one code path searches and opens. */
    fun submitToOmnibar(text: String) {
        val root = parent as? ViewGroup ?: rootView as? ViewGroup ?: return
        val input = runCatching { root.findViewById<EditText>(R.id.omnibarTextInput) }.getOrNull()
        if (input != null) {
            input.requestFocus()
            input.setText(text)
            input.setSelection(input.text.length)
            input.onEditorAction(EditorInfo.IME_ACTION_GO)
        } else {
            onOpenUrl?.invoke(text)
        }
    }

    fun hideSheets() {
        currentSheet?.dismiss()
        tabsDeck?.hide()
    }

    /* --------------------------------------------------------------------------------------- */
    /* Browser integration                                                                     */
    /* --------------------------------------------------------------------------------------- */

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hideUpstreamNewTabPage()
    }

    /**
     * The DuckDuckGo logo page underneath is hidden instead of removed: the browser still toggles
     * the panel it lives in, and its layout code still expects to find these views. INVISIBLE keeps
     * them laid out (so nothing shifts) but stops them drawing or taking touches.
     */
    private fun hideUpstreamNewTabPage() {
        val root = parent as? ViewGroup ?: return
        listOf(R.id.newTabPage, R.id.browserBackground, R.id.rebrandBrowserBackground).forEach { id ->
            runCatching { root.findViewById<View>(id)?.visibility = View.INVISIBLE }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        applyOmnibarInset()
    }

    /** Start the page directly under the browser's real address bar, on every screen size. */
    private fun applyOmnibarInset() {
        val root = parent as? ViewGroup ?: rootView as? ViewGroup ?: return
        val omnibar = runCatching { root.findViewById<View>(R.id.omnibarTextInput) }.getOrNull() ?: return
        val om = IntArray(2)
        val me = IntArray(2)
        omnibar.getLocationOnScreen(om)
        getLocationOnScreen(me)
        val bottom = om[1] + omnibar.height - me[1]
        val inset = (bottom + dp(14)).coerceAtLeast(dp(14))
        if (inset != appliedInset) {
            appliedInset = inset
            content.setPadding(dp(18), inset, dp(18), dp(120))
        }
    }

    /* --------------------------------------------------------------------------------------- */
    /* helpers                                                                                 */
    /* --------------------------------------------------------------------------------------- */

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun marginParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun animatePress(view: View) {
        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    /** Called by the host activity when the browser bar wants the page scrolled back up. */
    fun scrollToTop() {
        scroll.smoothScrollTo(0, 0)
    }

    /** Monogram colour of a source, exposed for the settings preview. */
    fun sourceColor(source: String): Int = monogramColor(source)
}
