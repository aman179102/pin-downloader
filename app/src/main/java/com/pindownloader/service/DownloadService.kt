package com.pindownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pindownloader.MainActivity
import com.pindownloader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUrl = intent?.getStringExtra("video_url") ?: return START_NOT_STICKY
        val title = intent.getStringExtra("title") ?: "Pinterest Video"
        startForeground(NOTIFICATION_ID, buildNotification("Starting…", 0, true))

        scope.launch {
            try {
                val filePath = if (videoUrl.contains(".m3u8")) {
                    downloadHls(videoUrl, title)
                } else {
                    downloadDirect(videoUrl, title)
                }
                showCompletionNotification(filePath, title)
                broadcastComplete(filePath)
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                stopForeground(STOP_FOREGROUND_REMOVE)
                showErrorNotification(e.message ?: "Download failed")
                broadcastError(e.message ?: "Download failed")
            }
            stopSelf()
        }

        return START_REDELIVER_INTENT
    }

    // ── Direct MP4 download ──

    private fun downloadDirect(url: String, title: String): String {
        updateNotification("Downloading video…", 10, false)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val body = response.body ?: throw Exception("Empty response body")
        val bytes = body.bytes()
        updateNotification("Saving video…", 80, false)
        val fileName = sanitizeFilename(title) + ".mp4"
        saveVideo(fileName, bytes)
        updateNotification("Done!", 100, false)
        return "Movies/PinDownloader/$fileName"
    }

    // ── HLS download (m3u8 → extract segments → mux video+audio → MP4) ──

    private data class HlsInit(val uri: String, val byteRangeLength: Long, val byteRangeStart: Long)
    private data class HlsSegment(val uri: String, val byteRangeLength: Long, val byteRangeStart: Long)

    private fun downloadHls(hlsUrl: String, title: String): String {
        updateNotification("Fetching playlist…", 5, false)
        val masterPlaylist = httpGetString(hlsUrl)
        val baseUrl = hlsUrl.substringBeforeLast("/")

        val (videoMediaUrl, audioUri) = resolveBestQuality(masterPlaylist, baseUrl, hlsUrl)

        updateNotification("Downloading video…", 20, false)
        val videoBytes = downloadAndExtractSegments(videoMediaUrl)

        var audioBytes: ByteArray? = null
        if (audioUri != null) {
            updateNotification("Downloading audio…", 50, false)
            try {
                val audioMediaUrl = if (audioUri.startsWith("http")) audioUri else "$baseUrl/$audioUri"
                audioBytes = downloadAndExtractSegments(audioMediaUrl)
            } catch (_: Exception) { }
        }

        val finalBytes = if (audioBytes != null) {
            updateNotification("Muxing audio & video…", 70, false)
            muxVideoAndAudio(videoBytes, audioBytes)
        } else {
            videoBytes
        }

        updateNotification("Saving…", 85, false)
        val fileName = sanitizeFilename(title) + ".mp4"
        saveVideo(fileName, finalBytes)
        updateNotification("Done!", 100, false)
        return "Movies/PinDownloader/$fileName"
    }

    private fun downloadAndExtractSegments(mediaPlaylistUrl: String): ByteArray {
        val playlist = httpGetString(mediaPlaylistUrl)
        val baseUrl = mediaPlaylistUrl.substringBeforeLast("/")

        val init = parseHlsInit(playlist, baseUrl)
        val segments = parseHlsSegments(playlist, baseUrl)

        if (segments.isEmpty()) throw Exception("No HLS segments found")

        val sourceUrl = segments.first().uri
        val sourceBytes = httpGetBytes(sourceUrl)

        val output = ByteArrayOutputStream()
        if (init != null) {
            output.write(extractBytes(sourceBytes, init.byteRangeStart, init.byteRangeLength))
        }
        for (seg in segments) {
            output.write(extractBytes(sourceBytes, seg.byteRangeStart, seg.byteRangeLength))
        }
        return output.toByteArray()
    }

    private fun resolveBestQuality(
        playlist: String, baseUrl: String, fallbackUrl: String
    ): Pair<String, String?> {
        val lines = playlist.lines()
        var bestBandwidth = -1L
        var bestUrl: String? = null
        var audioUri: String? = null

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    val bw = Regex("""BANDWIDTH=(\d+)""").find(line)
                        ?.groupValues?.get(1)?.toLongOrNull() ?: -1
                    i++
                    if (i < lines.size) {
                        val uri = lines[i].trim()
                        if (!uri.startsWith("#") && bw > bestBandwidth) {
                            bestBandwidth = bw
                            bestUrl = if (uri.startsWith("http")) uri else "$baseUrl/$uri"
                        }
                    }
                }
                line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") -> {
                    val uri = Regex("""URI="([^"]+)"""").find(line)
                        ?.groupValues?.get(1)
                    if (uri != null) audioUri = uri
                    i++
                }
                else -> i++
            }
        }

        return Pair(bestUrl ?: fallbackUrl, audioUri)
    }

    private fun parseHlsInit(playlist: String, baseUrl: String): HlsInit? {
        val pattern = Pattern.compile("""#EXT-X-MAP:URI="([^"]+)"(?:,BYTERANGE="(\d+)@(\d+)")?""")
        val matcher = pattern.matcher(playlist)
        return if (matcher.find()) {
            val uri = matcher.group(1) ?: return null
            val length = matcher.group(2)?.toLongOrNull() ?: -1
            val start = matcher.group(3)?.toLongOrNull() ?: 0
            val fullUri = if (uri.startsWith("http")) uri else "$baseUrl/$uri"
            HlsInit(fullUri, length, start)
        } else null
    }

    private fun parseHlsSegments(playlist: String, baseUrl: String): List<HlsSegment> {
        val segments = mutableListOf<HlsSegment>()
        val lines = playlist.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF") || line.startsWith("#EXT-X-BYTERANGE:")) {
                var brLength = -1L
                var brStart = -1L
                var uriLine: String? = null

                if (line.startsWith("#EXT-X-BYTERANGE:")) {
                    val br = parseByteRange(line.substringAfter("#EXT-X-BYTERANGE:"))
                    brLength = br.first; brStart = br.second
                    i++
                    if (i < lines.size && !lines[i].trim().startsWith("#")) {
                        uriLine = lines[i].trim()
                    }
                } else if (line.startsWith("#EXTINF:")) {
                    val brMatch = Regex("""#EXT-X-BYTERANGE:(\d+)@(\d+)""").find(line)
                    if (brMatch != null) {
                        brLength = brMatch.groupValues[1].toLong()
                        brStart = brMatch.groupValues[2].toLong()
                        var j = i + 1
                        while (j < lines.size && lines[j].trim().startsWith("#")) j++
                        if (j < lines.size) uriLine = lines[j].trim()
                        i = j
                    } else {
                        i++
                        if (i < lines.size) {
                            val next = lines[i].trim()
                            val br2 = Regex("""#EXT-X-BYTERANGE:(\d+)@(\d+)""").find(next)
                            if (br2 != null) {
                                brLength = br2.groupValues[1].toLong()
                                brStart = br2.groupValues[2].toLong()
                                i++
                                if (i < lines.size && !lines[i].trim().startsWith("#")) {
                                    uriLine = lines[i].trim()
                                }
                            } else if (!next.startsWith("#")) {
                                uriLine = next
                            }
                        }
                    }
                }

                if (uriLine != null && brLength > 0) {
                    val fullUri = if (uriLine.startsWith("http")) uriLine else "$baseUrl/$uriLine"
                    segments.add(HlsSegment(fullUri, brLength, brStart))
                }
            } else {
                i++
            }
        }
        return segments
    }

    private fun parseByteRange(s: String): Pair<Long, Long> {
        val parts = s.trim().split("@")
        val length = parts[0].toLong()
        val start = if (parts.size > 1) parts[1].toLong() else -1L
        return length to start
    }

    private fun extractBytes(source: ByteArray, start: Long, length: Long): ByteArray {
        return source.copyOfRange(start.toInt(), start.toInt() + length.toInt())
    }

    // ── Mux video + audio into a single MP4 using MediaMuxer ──

    private fun muxVideoAndAudio(videoBytes: ByteArray, audioBytes: ByteArray): ByteArray {
        val cacheDir = applicationContext.cacheDir
        val videoFile = File(cacheDir, "vid_${System.nanoTime()}.mp4")
        val audioFile = File(cacheDir, "aud_${System.nanoTime()}.m4a")
        val outputFile = File(cacheDir, "out_${System.nanoTime()}.mp4")

        try {
            videoFile.writeBytes(videoBytes)
            audioFile.writeBytes(audioBytes)

            val videoExtractor = MediaExtractor().apply {
                setDataSource(videoFile.absolutePath)
            }
            val audioExtractor = MediaExtractor().apply {
                setDataSource(audioFile.absolutePath)
            }

            val muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            var videoTrack = -1
            var audioTrack = -1

            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoExtractor.selectTrack(i)
                    videoTrack = muxer.addTrack(fmt)
                    break
                }
            }

            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioExtractor.selectTrack(i)
                    audioTrack = muxer.addTrack(fmt)
                    break
                }
            }

            if (videoTrack < 0 && audioTrack < 0) {
                throw Exception("No media tracks found")
            }

            muxer.start()

            val buf = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()

            fun writeTrack(extractor: MediaExtractor, track: Int) {
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buf, 0)
                    if (info.size < 0) break
                    info.presentationTimeUs = extractor.sampleTime
                    @Suppress("WrongConstant")
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(track, buf, info)
                    extractor.advance()
                }
            }

            if (videoTrack >= 0) writeTrack(videoExtractor, videoTrack)
            if (audioTrack >= 0) writeTrack(audioExtractor, audioTrack)

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()

            return outputFile.readBytes()
        } finally {
            videoFile.delete()
            audioFile.delete()
            outputFile.delete()
        }
    }

    // ── HTTP helpers ──

    private fun httpGetString(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
        return res.body?.string() ?: throw Exception("Empty response")
    }

    private fun httpGetBytes(url: String): ByteArray {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
        return res.body?.bytes() ?: throw Exception("Empty response")
    }

    // ── Save to storage ──

    private fun saveVideo(fileName: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(fileName, bytes)
        } else {
            saveToLegacy(fileName, bytes)
        }
    }

    private fun saveToMediaStore(fileName: String, bytes: ByteArray) {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/PinDownloader"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(bytes)
            outputStream.flush()
        } ?: throw Exception("Failed to open output stream")

        contentValues.clear()
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
    }

    private fun saveToLegacy(fileName: String, bytes: ByteArray) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "PinDownloader"
        )
        dir.mkdirs()

        val file = File(dir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also {
            it.data = android.net.Uri.fromFile(file)
            sendBroadcast(it)
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(100)
            .ifEmpty { "pinterest_video_${System.currentTimeMillis()}" }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_desc)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(text: String, progress: Int, indeterminate: Boolean) {
        val notification = buildNotification(text, progress, indeterminate)
        notificationManager.notify(NOTIFICATION_ID, notification)
        broadcastProgress(progress, text)
    }

    // ── Broadcast to UI ──

    private fun broadcastProgress(progress: Int, message: String) {
        sendBroadcast(Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            setPackage(packageName)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_MESSAGE, message)
        })
    }

    private fun broadcastComplete(filePath: String) {
        sendBroadcast(Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            setPackage(packageName)
            putExtra(EXTRA_FILE_PATH, filePath)
        })
    }

    private fun broadcastError(error: String) {
        sendBroadcast(Intent(ACTION_DOWNLOAD_ERROR).apply {
            setPackage(packageName)
            putExtra(EXTRA_MESSAGE, error)
        })
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("PinDownloader")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .build()

    private fun showCompletionNotification(filePath: String, title: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✓ Download Complete")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$title\n\nSaved to:\n$filePath"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    private fun showErrorNotification(error: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Download Failed")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "DownloadService"
        const val CHANNEL_ID = "pin_download_channel"
        const val NOTIFICATION_ID = 1001
        const val COMPLETE_NOTIFICATION_ID = 1002

        const val ACTION_DOWNLOAD_PROGRESS = "com.pindownloader.DOWNLOAD_PROGRESS"
        const val ACTION_DOWNLOAD_COMPLETE = "com.pindownloader.DOWNLOAD_COMPLETE"
        const val ACTION_DOWNLOAD_ERROR = "com.pindownloader.DOWNLOAD_ERROR"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_FILE_PATH = "file_path"
    }
}
