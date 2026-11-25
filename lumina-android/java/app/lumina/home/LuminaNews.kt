package app.lumina.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.LruCache
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One real headline. Nothing here is mocked: every field comes from the feed. */
data class NewsItem(
    val title: String,
    val url: String,
    val source: String,
    val domain: String,
    val publishedAt: Long,
    val imageUrl: String? = null,
)

/**
 * Real news, no API key, no mock data: Google News' public RSS, the same published feed every
 * news reader uses. Topics map to the section feeds, so the chips on the home page switch real
 * content instead of re-filtering a hardcoded list.
 */
object LuminaNewsRepository {

    data class Topic(val key: String, val label: String, val path: String)

    val topics: List<Topic> = listOf(
        Topic("top", "For you", ""),
        Topic("india", "India", "headlines/section/topic/NATION"),
        Topic("world", "World", "headlines/section/topic/WORLD"),
        Topic("tech", "Tech", "headlines/section/topic/TECHNOLOGY"),
        Topic("business", "Business", "headlines/section/topic/BUSINESS"),
        Topic("sports", "Sports", "headlines/section/topic/SPORTS"),
        Topic("ent", "Entertainment", "headlines/section/topic/ENTERTAINMENT"),
    )

    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, Pair<Long, List<NewsItem>>>()
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun cached(topic: String): List<NewsItem>? {
        val entry = cache[topic] ?: return null
        if (System.currentTimeMillis() - entry.first > CACHE_TTL_MS) return null
        return entry.second
    }

    /** Loads [topic] off the main thread and answers on it. Failures give an empty list, never a crash. */
    fun load(topic: Topic, force: Boolean = false, onResult: (List<NewsItem>) -> Unit) {
        if (!force) {
            cached(topic.key)?.let {
                onResult(it)
                return
            }
        }
        io.execute {
            val items = runCatching { fetch(topic) }.getOrDefault(emptyList())
            if (items.isNotEmpty()) {
                cache[topic.key] = System.currentTimeMillis() to items
            }
            main.post { onResult(items) }
        }
    }

    private fun feedUrl(topic: Topic): String {
        val section = if (topic.path.isEmpty()) "" else "/" + topic.path
        return "https://news.google.com/rss$section?hl=en-IN&gl=IN&ceid=IN:en"
    }

    private fun fetch(topic: Topic): List<NewsItem> {
        val xml = httpGet(feedUrl(topic)) ?: return emptyList()
        return parse(xml)
    }

    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
                )
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > 4 * 1024 * 1024) break
                }
                buffer.toString("UTF-8")
            }
        } catch (ignored: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parse(xml: String): List<NewsItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())
        val items = ArrayList<NewsItem>(24)

        var inItem = false
        var title: String? = null
        var link: String? = null
        var pubDate: String? = null
        var sourceName: String? = null
        var sourceDomain: String? = null
        var image: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = null; link = null; pubDate = null
                        sourceName = null; sourceDomain = null; image = null
                    }
                    "title" -> if (inItem && title == null) title = parser.nextTextSafely()
                    "link" -> if (inItem && link == null) link = parser.nextTextSafely()
                    "pubDate" -> if (inItem) pubDate = parser.nextTextSafely()
                    "source" -> if (inItem) {
                        sourceDomain = parser.getAttributeValue(null, "url")
                        sourceName = parser.nextTextSafely()
                    }
                    "content" -> if (inItem && image == null) {
                        image = parser.getAttributeValue(null, "url")
                    }
                    "enclosure" -> if (inItem && image == null) {
                        image = parser.getAttributeValue(null, "url")
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    val cleanTitle = clean(title)
                    val cleanLink = link?.trim().orEmpty()
                    if (!cleanTitle.isNullOrEmpty() && cleanLink.isNotEmpty()) {
                        val domain = domainOf(sourceDomain, cleanLink)
                        items.add(
                            NewsItem(
                                title = cleanTitle,
                                url = cleanLink,
                                source = sourceName?.trim()?.ifEmpty { domain } ?: domain,
                                domain = domain,
                                publishedAt = parseDate(pubDate),
                                imageUrl = image,
                            ),
                        )
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun XmlPullParser.nextTextSafely(): String? = try {
        nextText()
    } catch (ignored: Exception) {
        null
    }

    private fun clean(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val decoded = runCatching {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrDefault(raw)
        // Google News appends " - Publisher" to every headline; the card shows the source anyway.
        return decoded.replace(Regex("\\s+-\\s+[^-]{1,40}$"), "").trim()
    }

    private fun domainOf(sourceUrl: String?, link: String): String {
        val candidate = sourceUrl ?: link
        return runCatching {
            val host = URL(candidate).host.lowercase(Locale.US)
            host.removePrefix("www.")
        }.getOrDefault("")
    }

    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (pattern in formats) {
            runCatching {
                val value = SimpleDateFormat(pattern, Locale.US).parse(raw)
                if (value != null) return value.time
            }
        }
        return 0L
    }

    fun relativeTime(time: Long): String {
        if (time <= 0L) return ""
        val minutes = (System.currentTimeMillis() - time) / 60000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
    }
}

/** Publisher favicons, fetched from DuckDuckGo's icon service (no Google, no tracking pixels). */
object LuminaFavicons {

    private val cache = object : LruCache<String, Bitmap>(96) {}
    private val executor = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    fun load(domain: String, onBitmap: (Bitmap?) -> Unit) {
        if (domain.isEmpty()) {
            onBitmap(null)
            return
        }
        cache[domain]?.let {
            onBitmap(it)
            return
        }
        executor.execute {
            val bitmap = runCatching { download(domain) }.getOrNull()
            if (bitmap != null) cache.put(domain, bitmap)
            main.post { onBitmap(bitmap) }
        }
    }

    private fun download(domain: String): Bitmap? {
        val url = "https://icons.duckduckgo.com/ip3/$domain.ico"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }
}
