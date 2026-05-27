package com.pindownloader.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pindownloader.model.PinInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PinterestScraper {

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    suspend fun fetchPinInfo(pinUrl: String): Result<PinInfo> = withContext(Dispatchers.IO) {
        try {
            val cleanedUrl = pinUrl.trim()
                .split(" ")
                .firstOrNull { it.startsWith("http") }
                ?: pinUrl
            val resolvedUrl = resolveUrl(cleanedUrl)
            val pinId = extractPinId(resolvedUrl)
            if (pinId == null) {
                return@withContext Result.failure(Exception("Invalid Pinterest URL"))
            }

            var pinInfo = tryPostApi(pinId, resolvedUrl)
            if (pinInfo == null) pinInfo = tryScrape(pinId, resolvedUrl)
            if (pinInfo == null) pinInfo = tryOembed(pinId, resolvedUrl)

            if (pinInfo != null && pinInfo.isVideo) {
                Result.success(pinInfo)
            } else if (pinInfo != null) {
                Result.failure(Exception("No video found in this pin"))
            } else {
                Result.failure(Exception("Could not fetch pin data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── URL helpers ──

    private fun resolveUrl(url: String): String {
        if (url.contains("pin.it")) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .build()
            val response = client.newCall(request).execute()
            return response.request.url.toString()
        }
        return url
    }

    private fun extractPinId(url: String): String? {
        val match = Regex("""/pin/(\d+)/?""").find(url)
        return match?.groupValues?.get(1)
    }

    private fun userAgent() = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"

    private fun csrfFromCookies(): String? {
        val cookies = cookieStore["www.pinterest.com"] ?: cookieStore[".pinterest.com"]
        return cookies?.firstOrNull { it.name == "csrftoken" }?.value
    }

    // ── Strategy 1 (primary): POST to Pinterest internal API with CSRF ──

    private fun tryPostApi(pinId: String, pinUrl: String): PinInfo? {
        return try {
            ensureSession()
            val csrf = csrfFromCookies()
            if (csrf.isNullOrEmpty()) return null

            val body = FormBody.Builder()
                .add("source_url", "/pin/$pinId/")
                .add("data", """{"options":{"id":"$pinId","field_set_key":"detailed"},"context":{}}""")
                .build()

            val req = Request.Builder()
                .url("https://www.pinterest.com/resource/PinResource/get/")
                .header("User-Agent", userAgent())
                .header("Accept", "application/json, text/javascript, */*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-CSRFToken", csrf)
                .header("Referer", "https://www.pinterest.com/pin/$pinId/")
                .post(body)
                .build()

            val res = client.newCall(req).execute()
            val json = res.body?.string() ?: return null
            val root = JsonParser.parseString(json).asJsonObject
            val data = root.getAsJsonObject("resource_response")?.getAsJsonObject("data")
            if (data != null) {
                val info = parsePinData(data, pinUrl)
                if (info != null && info.isVideo) return info
            }
            null
        } catch (_: Exception) { null }
    }

    private fun ensureSession() {
        if (csrfFromCookies() != null) return
        client.newCall(
            Request.Builder()
                .url("https://www.pinterest.com/")
                .header("User-Agent", userAgent())
                .build()
        ).execute().close()
    }

    // ── Strategy 2 (fallback): scrape HTML ──

    private fun tryScrape(pinId: String, pinUrl: String): PinInfo? {
        return try {
            val html = fetchHtml("https://www.pinterest.com/pin/$pinId/")

            val scriptRegex = Regex(
                """<script[^>]+type="application/json"[^>]*>(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL
            )
            for (match in scriptRegex.findAll(html)) {
                try {
                    val root = JsonParser.parseString(match.groupValues[1]).asJsonObject
                    val redux = root.getAsJsonObject("props")
                        ?.getAsJsonObject("pageProps")
                        ?.getAsJsonObject("initialReduxState")
                        ?: root.getAsJsonObject("initialReduxState")
                    if (redux != null) {
                        val pins = redux.getAsJsonObject("pins")
                        if (pins != null) {
                            for (key in pins.keySet()) {
                                val info = parsePinData(pins.getAsJsonObject(key), pinUrl)
                                if (info != null && info.isVideo) return info
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            parseFallback(html, pinUrl)
        } catch (_: Exception) { null }
    }

    private fun fetchHtml(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
        return res.body?.string() ?: throw Exception("Empty response")
    }

    // ── Strategy 3 (last resort): oEmbed ──

    private fun tryOembed(pinId: String, pinUrl: String): PinInfo? {
        return try {
            val json = fetchHtml(
                "https://www.pinterest.com/oembed.json?url=https://www.pinterest.com/pin/$pinId/"
            )
            val obj = JsonParser.parseString(json).asJsonObject
            PinInfo(
                title = obj.get("title")?.asString ?: "",
                thumbnailUrl = obj.get("thumbnail_url")?.asString ?: "",
                isVideo = false,
                pinUrl = pinUrl
            )
        } catch (_: Exception) { null }
    }

    // ── Fallback HTML regex ──

    private fun parseFallback(html: String, pinUrl: String): PinInfo? {
        val videoUrlRegex = Regex("""https://[^"'\s]*(?:v\.pinimg\.com|v1\.pinimg\.com)[^"'\s]*(?:\.mp4|\.m3u8)""")
        val urls = videoUrlRegex.findAll(html).map { it.value }.toList().distinct()
        if (urls.isEmpty()) return null

        val qualities = mutableMapOf<String, String>()
        urls.forEachIndexed { i, u -> qualities["V_${i}"] = u }

        val ogImage = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)"[^>]*>""")
            .find(html)?.groupValues?.get(1) ?: ""
        val ogTitle = Regex("""<meta[^>]+property="og:title"[^>]+content="([^"]+)"[^>]*>""")
            .find(html)?.groupValues?.get(1) ?: ""

        return PinInfo(ogTitle, ogImage, qualities, true, pinUrl)
    }

    // ── Parse pin JSON ──

    private fun parsePinData(pin: JsonObject, pinUrl: String): PinInfo? {
        val qualities = mutableMapOf<String, String>()

        extractDirectVideos(pin, qualities)

        if (qualities.isEmpty()) {
            extractStoryPinVideos(pin, qualities)
        }

        if (qualities.isEmpty()) return null

        val title = pin.get("title")?.asString
            ?: pin.getAsJsonObject("seo")?.get("title")?.asString
            ?: pin.get("description")?.asString
            ?: pin.get("grid_description")?.asString
            ?: ""

        val images = pin.getAsJsonObject("images")
        val thumb = images?.get("orig")?.asJsonObject?.get("url")?.asString
            ?: images?.get("600x315")?.asJsonObject?.get("url")?.asString
            ?: images?.get("236x")?.asJsonObject?.get("url")?.asString
            ?: pin.get("image_medium_url")?.asString
            ?: pin.get("image_cover_url")?.asString
            ?: pin.get("thumbnail")?.asString
            ?: ""

        return PinInfo(title, thumb, qualities, true, pinUrl)
    }

    private fun extractDirectVideos(pin: JsonObject, qualities: MutableMap<String, String>) {
        val videos = pin.getAsJsonObject("videos")
        if (videos != null) {
            val innerList = videos.getAsJsonArray("video_list")
            if (innerList != null) {
                for (el in innerList) {
                    val url = el.asJsonObject?.get("url")?.asString
                    if (url != null && isValidVideoUrl(url)) {
                        qualities[el.asJsonObject.get("type")?.asString ?: "V_${qualities.size}"] = url
                    }
                }
            } else {
                for (key in videos.keySet()) {
                    val v = videos.getAsJsonObject(key)
                    val url = extractVideoUrl(v)
                    if (url != null) qualities[key] = url
                }
            }
        }

        val videoList = pin.getAsJsonArray("video_list")
        if (videoList != null) {
            for (el in videoList) {
                val url = el.asJsonObject?.get("url")?.asString
                if (url != null && isValidVideoUrl(url)) {
                    qualities[el.asJsonObject.get("type")?.asString ?: "V_${qualities.size}"] = url
                }
            }
        }
    }

    private fun extractStoryPinVideos(pin: JsonObject, qualities: MutableMap<String, String>) {
        val story = pin.getAsJsonObject("story_pin_data")
        val pages = story?.getAsJsonArray("pages") ?: return

        for (page in pages) {
            val videoObj = page.asJsonObject.getAsJsonObject("video") ?: continue
            val videoList = videoObj.getAsJsonObject("video_list") ?: continue

            for (key in videoList.keySet()) {
                val entry = videoList.getAsJsonObject(key)
                val url = entry?.get("url")?.asString
                if (url != null && isValidVideoUrl(url)) {
                    qualities[key] = url
                }
            }
        }
    }

    private fun extractVideoUrl(obj: JsonObject?): String? {
        if (obj == null) return null
        val candidates = listOf(
            obj.get("url")?.asString,
            obj.get("source")?.asString,
            obj.getAsJsonObject("source")?.get("url")?.asString,
            obj.get("video_url")?.asString,
            obj.get("download_url")?.asString,
            obj.get("src")?.asString,
            obj.get("source_url")?.asString
        )
        return candidates.firstOrNull { it != null && isValidVideoUrl(it) }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        return url.contains("pinimg.com") &&
               (url.contains(".mp4") || url.contains(".m3u8") || url.contains(".cmfv") || url.contains("videos"))
    }
}
