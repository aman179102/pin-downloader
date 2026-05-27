// SPDX-License-Identifier: GPL-3.0-only
package com.pindownloader.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.pindownloader.data.PinterestScraper
import com.pindownloader.model.PinInfo
import com.pindownloader.service.DownloadService
import com.pindownloader.ui.theme.GradientEnd
import com.pindownloader.ui.theme.GradientStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(sharedText: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(sharedText ?: "") }
    var pinInfo by remember { mutableStateOf<PinInfo?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }
    var downloadProgressPct by remember { mutableStateOf(0) }
    var downloadCompleteMsg by remember { mutableStateOf<String?>(null) }
    var downloadErrorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(downloadCompleteMsg, downloadErrorMsg) {
        if (downloadCompleteMsg != null || downloadErrorMsg != null) {
            delay(8000)
            downloadCompleteMsg = null
            downloadErrorMsg = null
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    DownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                        isDownloading = true
                        downloadProgress = intent.getStringExtra(DownloadService.EXTRA_MESSAGE) ?: ""
                        downloadProgressPct = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                        downloadCompleteMsg = null; downloadErrorMsg = null
                    }
                    DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                        isDownloading = false; downloadProgress = ""; downloadProgressPct = 0
                        downloadCompleteMsg = "Saved to " + (intent.getStringExtra(DownloadService.EXTRA_FILE_PATH) ?: "")
                        downloadErrorMsg = null
                    }
                    DownloadService.ACTION_DOWNLOAD_ERROR -> {
                        isDownloading = false; downloadProgress = ""; downloadProgressPct = 0
                        downloadErrorMsg = intent.getStringExtra(DownloadService.EXTRA_MESSAGE) ?: "Unknown error"
                        downloadCompleteMsg = null
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS)
            addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadService.ACTION_DOWNLOAD_ERROR)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun fetchPin() {
        val input = url.trim()
        if (input.isBlank()) { fetchError = "Please enter a Pinterest URL"; return }
        downloadCompleteMsg = null; downloadErrorMsg = null; fetchError = null
        downloadProgress = ""; isDownloading = false; pinInfo = null
        scope.launch {
            isFetching = true
            PinterestScraper.fetchPinInfo(input)
                .onSuccess { info ->
                    if (info.isVideo || info.bestVideoUrl != null) pinInfo = info
                    else fetchError = "No video found in this pin"
                }
                .onFailure { fetchError = it.message ?: "Failed to fetch pin" }
            isFetching = false
        }
    }

    fun startDownload(info: PinInfo) {
        val videoUrl = info.bestVideoUrl ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) { Toast.makeText(context, "Notification permission required", Toast.LENGTH_SHORT).show(); return }
        }
        isDownloading = true; downloadProgress = "Starting download…"; downloadProgressPct = 0
        downloadCompleteMsg = null; downloadErrorMsg = null
        context.startForegroundService(Intent(context, DownloadService::class.java).apply {
            putExtra("video_url", videoUrl); putExtra("title", info.title)
        })
    }

    LaunchedEffect(sharedText) { sharedText?.let { url = it; fetchPin() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Download, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("PinDownloader", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground)
                            Text("Video Downloader", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Status chip ──
                StatusChip(isDownloading = isDownloading, pinInfo = pinInfo)

                Spacer(Modifier.height(16.dp))

                // ── URL Input Card ──
                UrlInputCard(
                    url = url,
                    onUrlChange = { url = it },
                    onFetch = { fetchPin() },
                    isFetching = isFetching,
                )

                Spacer(Modifier.height(20.dp))

                // ── Content ──
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isFetching -> LoadingSkeleton()
                        fetchError != null -> ErrorCard(fetchError!!) { fetchPin() }
                        pinInfo != null -> VideoPreviewCard(
                            info = pinInfo!!,
                            isDownloading = isDownloading,
                            downloadProgress = downloadProgress,
                            downloadProgressPct = downloadProgressPct,
                            onDownload = { startDownload(pinInfo!!) }
                        )
                        else -> EmptyState()
                    }
                }
            }
        }
    }
}

// ── Status Chip ──

@Composable
private fun StatusChip(isDownloading: Boolean, pinInfo: PinInfo?) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isDownloading -> MaterialTheme.colorScheme.tertiaryContainer
            pinInfo != null -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isDownloading -> MaterialTheme.colorScheme.onTertiaryContainer
            pinInfo != null -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chipText"
    )
    val icon: ImageVector = when {
        isDownloading -> Icons.Filled.Sync
        pinInfo != null -> Icons.Filled.CheckCircle
        else -> Icons.Outlined.Circle
    }
    val label = when {
        isDownloading -> "Downloading…"
        pinInfo != null -> "Ready to download"
        else -> "Ready"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(14.dp), tint = textColor)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = textColor, fontWeight = FontWeight.Medium)
        }
    }
}

// ── URL Input Card ──

@Composable
private fun UrlInputCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onFetch: () -> Unit,
    isFetching: Boolean,
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    placeholder = {
                        Text("Paste Pinterest URL",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onFetch() }),
                    modifier = Modifier.weight(1f)
                )

                // Paste
                IconButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        clip?.primaryClip?.getItemAt(0)?.text?.toString()?.let { onUrlChange(it) }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Outlined.ContentPaste,
                        contentDescription = "Paste",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Animated gradient button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                GradientButton(
                    onClick = onFetch,
                    enabled = url.isNotBlank() && !isFetching,
                    isLoading = isFetching,
                    text = if (isFetching) "Analyzing…" else "Analyze Link",
                    icon = if (isFetching) null else Icons.Filled.Search
                )
            }
        }
    }
}

// ── Gradient Button ──

@Composable
private fun GradientButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean = false,
    text: String,
    icon: ImageVector? = null,
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled && !isLoading) 1f else 0.97f, label = "scale"
    )
    val animProgress = rememberInfiniteTransition(label = "btnPulse")
    val shimmerAlpha by animProgress.animateFloat(
        initialValue = 0f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "shimmer"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .scale(scale),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                    else Brush.horizontalGradient(listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant
                    )),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Shimmer overlay
            if (enabled && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.White.copy(alpha = shimmerAlpha),
                            RoundedCornerShape(14.dp)
                        )
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Empty State ──

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Gradient circle
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(listOf(GradientStart, GradientEnd, GradientStart))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Download, contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White)
        }
        Spacer(Modifier.height(28.dp))
        Text("PinDownloader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Paste a Pinterest video URL above\nto download in full quality",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(40.dp))
        // Quick tip
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Tip: Share a pin from the Pinterest app to quickly download",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp)
            }
        }
    }
}

// ── Loading Skeleton ──

@Composable
private fun LoadingSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceVariant

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(base))
            Column(Modifier.padding(20.dp)) {
                Box(Modifier.fillMaxWidth(0.7f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(base))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(base))
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(base))
            }
        }
    }
}

// ── Video Preview Card ──

@Composable
private fun VideoPreviewCard(
    info: PinInfo,
    isDownloading: Boolean,
    downloadProgress: String,
    downloadProgressPct: Int,
    onDownload: () -> Unit,
) {
    val isHls = info.isHls

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Title
            if (info.title.isNotBlank()) {
                Text(info.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
            }

            // Tags row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(label = "HD Video", icon = Icons.Filled.Videocam)
                Spacer(Modifier.width(8.dp))
                if (isHls) Tag(label = "HLS", icon = Icons.Filled.Hd)
                Spacer(Modifier.width(8.dp))
                Tag(label = "Pinterest", icon = Icons.Filled.PinDrop)
            }

            Spacer(Modifier.height(20.dp))

            // Download section
            AnimatedContent(targetState = isDownloading, label = "downloadSection") { down ->
                if (down) {
                    DownloadProgressSection(
                        progress = downloadProgress,
                        progressPct = downloadProgressPct
                    )
                } else {
                    GradientButton(
                        onClick = onDownload,
                        enabled = true,
                        text = "Download Best Quality",
                        icon = Icons.Filled.Download
                    )
                }
            }
        }
    }
}

// ── Tag Chip ──

@Composable
private fun Tag(label: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ── Download Progress ──

@Composable
private fun DownloadProgressSection(progress: String, progressPct: Int) {
    val animatedPct by animateFloatAsState(
        targetValue = progressPct / 100f,
        animationSpec = tween(400), label = "progress"
    )

    Column {
        LinearProgressIndicator(
            progress = { animatedPct },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = GradientStart,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(progress, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${progressPct}%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Error Card ──

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(14.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
