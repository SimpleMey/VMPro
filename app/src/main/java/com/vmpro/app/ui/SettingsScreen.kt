package com.vmpro.app.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vmpro.app.BuildConfig
import com.vmpro.app.R
import com.vmpro.app.analytics.Analytics
import com.vmpro.app.data.formatBytes
import com.vmpro.app.util.DownloadedFile
import com.vmpro.app.util.Downloader
import com.vmpro.app.util.DownloadsRepo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val context = LocalContext.current
    val count = remember { DownloadsRepo.list(context).size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SettingsBar("Settings", onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
        ) {
            SettingRow(
                icon = Icons.Filled.Download,
                title = "Downloads",
                subtitle = when (count) {
                    0 -> "No downloaded files"
                    1 -> "1 file"
                    else -> "$count files"
                },
                onClick = onOpenDownloads,
            )

            GroupLabel("Appearance")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleTheme() },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dark mode", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Switch between light and dark",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = isDark,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            GroupLabel("My device")
            val maker = remember { Build.MANUFACTURER.replaceFirstChar { it.uppercase() } }
            val model = remember { Build.MODEL ?: "" }
            val arch = remember { Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown" }
            val storage = remember { deviceStorage() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceBlock(Icons.Filled.PhoneAndroid, "Manufacturer", maker, model, Modifier.weight(1f))
                DeviceBlock(Icons.Filled.Memory, "Architecture", arch, "CPU ABI", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceBlock(Icons.Filled.Storage, "Storage", storage.first, storage.second, Modifier.weight(1f))
                DeviceBlock(
                    Icons.Filled.Android, "Android", "Android ${Build.VERSION.RELEASE}",
                    "API ${Build.VERSION.SDK_INT}", Modifier.weight(1f),
                )
            }

            GroupLabel("FAQ")
            FAQ.forEach { FaqItem(it) }

            GroupLabel("About")
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vmpro_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("VMPro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "VMPro is a simple manager app for Android apps that are available in GitHub repos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))

            LinkCard(
                icon = Icons.Filled.Language, title = "Website", subtitle = "vmpro.app",
                onClick = { Downloader.openUrl(context, "https://vmpro.app") },
            )
            LinkCard(
                icon = Icons.Filled.Code, title = "Open source",
                subtitle = "github.com/SimpleMey/VMPro",
                onClick = { Downloader.openUrl(context, "https://github.com/SimpleMey/VMPro") },
            )
            LinkCard(
                icon = Icons.AutoMirrored.Filled.Send, title = "Telegram channel",
                subtitle = "t.me/vmproapp",
                onClick = { Downloader.openTelegram(context, "vmproapp") },
            )
            LinkCard(
                icon = Icons.Filled.Share, title = "Share VMPro",
                subtitle = "Send the app (APK) to a friend", showOpenIcon = false,
                onClick = {
                    Analytics.appShared()
                    Downloader.shareApk(context)
                },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "This app does not host, build, or modify any files. Downloads come directly " +
                    "from the official GitHub release pages. Credit for the patches and modules " +
                    "belongs to their respective authors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(DownloadsRepo.list(context)) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsBar(
                title = "Downloads",
                onBack = onBack,
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.FolderOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No downloaded files",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Apps you download from VMPro show up here.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { file ->
                    DownloadRow(file) {
                        DownloadsRepo.delete(context, file.id)
                        items = DownloadsRepo.list(context)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all downloads") },
            text = { Text("Delete every file VMPro downloaded? You can download them again anytime.") },
            confirmButton = {
                TextButton(onClick = {
                    DownloadsRepo.clearAll(context)
                    items = DownloadsRepo.list(context)
                    confirmClear = false
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun DownloadRow(file: DownloadedFile, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Filled.Download)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatBytes(file.bytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete ${file.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---- shared bits ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun LinkCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showOpenIcon: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable { onClick() },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (showOpenIcon) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
}

// ---- My device ----

@Composable
private fun DeviceBlock(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Internal storage as (free, "free of total"); best-effort. */
private fun deviceStorage(): Pair<String, String> = try {
    val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
    formatBytes(stat.availableBytes) to "free of ${formatBytes(stat.totalBytes)}"
} catch (_: Exception) {
    "Unknown" to ""
}

// ---- FAQ ----

private data class Faq(
    val q: String,
    val a: String,
    val linkLabel: String? = null,
    val linkUrl: String? = null,
)

private val FAQ = listOf(
    Faq("What is VMPro?", "A simple manager for apps hosted in GitHub repos."),
    Faq(
        "How does VMPro build apps?",
        "VMPro does not build any app. It fetches publicly available apps from GitHub repos.",
    ),
    Faq("What is my device architecture?", "It is shown in the My device section above."),
    Faq(
        "Do I need to download apps for my architecture?",
        "Yes, but VMPro automatically detects your device architecture and offers the right " +
            "build, so no worries.",
    ),
    Faq(
        "Does VMPro send my data to any private organisation?",
        "No, your data stays with you. VMPro uses open source analytics (PostHog) only to know " +
            "how many people use the app.",
        "github.com/PostHog/posthog",
        "https://github.com/PostHog/posthog",
    ),
    Faq("App not installed?", "Uninstall the old app and try installing again."),
)

@Composable
private fun FaqItem(faq: Faq) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(faq.q, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        faq.a,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                    if (faq.linkUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            faq.linkLabel ?: faq.linkUrl,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { Downloader.openUrl(context, faq.linkUrl) },
                        )
                    }
                }
            }
        }
    }
}
