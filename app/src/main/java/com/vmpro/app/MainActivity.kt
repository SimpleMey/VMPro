package com.vmpro.app

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import com.vmpro.app.data.isNewerVersion
import com.vmpro.app.data.sameVersionFamily
import com.vmpro.app.ui.AboutScreen
import com.vmpro.app.ui.CatalogItem
import com.vmpro.app.ui.InstalledApp
import com.vmpro.app.ui.ManagerViewModel
import com.vmpro.app.ui.Section
import com.vmpro.app.ui.SharePrompt
import com.vmpro.app.ui.TAB_APPS
import com.vmpro.app.ui.TAB_MICROG
import com.vmpro.app.ui.TAB_MODULES
import com.vmpro.app.ui.TAB_TITLES
import com.vmpro.app.ui.TAB_TV
import com.vmpro.app.ui.TabState
import com.vmpro.app.ui.ThemePrefs
import com.vmpro.app.ui.VmproTheme
import androidx.core.view.WindowCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import com.vmpro.app.R
import com.vmpro.app.analytics.Analytics
import com.vmpro.app.util.Downloader
import com.vmpro.app.util.DownloadPhase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()
            var isDark by remember { mutableStateOf(ThemePrefs.get(context) ?: systemDark) }

            // Keep the status-bar icons legible against the current theme.
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }

            VmproTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App(
                        isDark = isDark,
                        onToggleTheme = {
                            isDark = !isDark
                            ThemePrefs.set(context, isDark)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun App(isDark: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var showShareExit by remember { mutableStateOf(false) }

    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
    } else {
        // Intercept the exit back-press to nudge sharing — but only once per app version.
        BackHandler(enabled = !showShareExit) {
            if (SharePrompt.isDoneFor(context, BuildConfig.VERSION_NAME)) {
                (context as? Activity)?.finish()
            } else {
                showShareExit = true
            }
        }
        ManagerScreen(
            onOpenAbout = { showAbout = true },
            isDark = isDark,
            onToggleTheme = onToggleTheme,
        )
    }

    if (showShareExit) {
        ShareExitDialog(
            onShare = {
                Analytics.appShared()
                Downloader.shareApk(context)
                SharePrompt.markDone(context, BuildConfig.VERSION_NAME)
                showShareExit = false
            },
            onAlreadyShared = {
                SharePrompt.markDone(context, BuildConfig.VERSION_NAME)
                showShareExit = false
                (context as? Activity)?.finish()
            },
            onClose = {
                showShareExit = false
                (context as? Activity)?.finish()
            },
        )
    }
}

/**
 * Shown when the user tries to exit: a gentle nudge to share the APK with a friend.
 * "X" / back closes the app (asks again next launch); "Share" opens the share sheet and
 * stops asking for this version; "Already shared" stops asking and closes the app. The
 * "stop asking" state re-arms after every app update (see [SharePrompt]).
 */
@Composable
private fun ShareExitDialog(
    onShare: () -> Unit,
    onAlreadyShared: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vmpro_logo),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Share VMPro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enjoying VMPro? Send the app to a friend so they can grab the " +
                            "latest builds too.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onShare,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onAlreadyShared, modifier = Modifier.fillMaxWidth()) {
                        Text("Already shared")
                    }
                }
            }
        }
    }
}

private data class NavDest(val tab: Int, val icon: ImageVector, val label: String)

/** Top-level destinations in the bottom bar. "Phone" holds the inner Apps/MicroG/Modules tabs. */
private val BOTTOM_DESTS = listOf(
    NavDest(TAB_APPS, Icons.Filled.PhoneAndroid, "Phone"),
    NavDest(TAB_TV, Icons.Filled.Tv, "TV"),
)

/** Suggests switching to the other MicroG when one is already installed. */
private data class MicrogSwitch(val installedLabel: String, val target: CatalogItem)

/**
 * GmsCore and MicroG RE share one package, so only the installed version tells them apart:
 * GmsCore uses 0.x.y builds, MicroG RE uses 6.x. If one is installed, suggest the other.
 */
private fun computeMicrogSwitch(
    sections: List<Section>,
    installed: Map<String, InstalledApp>,
): MicrogSwitch? {
    val items = sections.flatMap { it.items }
    val gmscore = items.find { it.label == "GmsCore" } ?: return null
    val microgre = items.find { it.label == "MicroG RE" } ?: return null
    val pkg = gmscore.packages.firstOrNull() ?: return null
    val installedVersion = installed[pkg]?.versionName ?: return null

    val isGmscore = when {
        gmscore.details?.version == installedVersion -> true
        microgre.details?.version == installedVersion -> false
        installedVersion.startsWith("0") -> true
        else -> false
    }
    val target = if (isGmscore) microgre else gmscore
    if (target.asset == null) return null
    return MicrogSwitch(if (isGmscore) "GmsCore" else "MicroG RE", target)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerScreen(
    onOpenAbout: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: ManagerViewModel = viewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val phases by viewModel.downloadPhases.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val installedPatches by viewModel.installedPatches.collectAsStateWithLifecycle()
    val conflict by viewModel.conflict.collectAsStateWithLifecycle()
    val updateVersion by viewModel.updateVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val state = states[selectedTab] ?: TabState.Loading
    val hazeState = remember { HazeState() }
    // Remember which inner tab (Apps/MicroG/Modules) to return to from the TV destination.
    var lastInner by remember { mutableStateOf(TAB_APPS) }
    if (selectedTab != TAB_TV) lastInner = selectedTab
    // On Android TV, drop initial focus into the list so the D-pad starts there (not on phones).
    val isTvDevice = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    // Re-check installed apps whenever the user returns to the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshInstalled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_vmpro_logo),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("VMPro", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                            )
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onOpenAbout) {
                            Icon(Icons.Outlined.Info, contentDescription = "About")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                updateVersion?.let { v ->
                    UpdateBanner(
                        version = v,
                        onUpdate = {
                            Analytics.updateBannerClicked()
                            Downloader.openUrl(context, "https://vmpro.app")
                        },
                    )
                }
                // Inner tabs live under the "Apps" destination only.
                if (selectedTab != TAB_TV) {
                    InnerTabs(selected = selectedTab, onSelect = { viewModel.selectTab(it) })
                }
            }
        },
        bottomBar = {
            // Full-width frosted-glass bar: blurs the list behind it (Android 12+),
            // falls back to the tint on older devices.
            Box(
                Modifier
                    .fillMaxWidth()
                    .hazeChild(state = hazeState),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    BOTTOM_DESTS.forEach { dest ->
                        val isTv = dest.tab == TAB_TV
                        NavigationBarItem(
                            selected = if (isTv) selectedTab == TAB_TV else selectedTab != TAB_TV,
                            onClick = { viewModel.selectTab(if (isTv) TAB_TV else lastInner) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .haze(
                    state = hazeState,
                    style = HazeStyle(
                        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        blurRadius = 22.dp,
                    ),
                ),
        ) {
        when (val s = state) {
            is TabState.Loading -> CenterBox(padding) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is TabState.Error -> CenterBox(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }

            is TabState.Success -> SectionList(
                sections = s.sections,
                padding = padding,
                phases = phases,
                progress = progress,
                installed = installed,
                installedPatches = installedPatches,
                requestInitialFocus = isTvDevice,
                onAction = viewModel::onAction,
                notice = if (selectedTab == TAB_MICROG) {
                    "Only one MicroG can be installed at a time — GmsCore and MicroG RE " +
                        "share the same package name, so installing one replaces the other."
                } else null,
                switch = if (selectedTab == TAB_MICROG) {
                    computeMicrogSwitch(s.sections, installed)
                } else null,
            )
        }
        }
    }

    conflict?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConflict() },
            title = { Text("App already installed") },
            text = {
                Text(
                    "“${info.label}” shares a package (${info.packageName}) with an app " +
                        "already on your device. They can't coexist — installing may fail " +
                        "unless you remove the existing one first.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.uninstallConflict() }) {
                    Text("Uninstall existing", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.installAnyway() }) { Text("Install anyway") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun CenterBox(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SectionList(
    sections: List<Section>,
    padding: PaddingValues,
    phases: Map<String, DownloadPhase>,
    progress: Map<String, Int>,
    installed: Map<String, InstalledApp>,
    installedPatches: Map<String, String>,
    requestInitialFocus: Boolean = false,
    onAction: (CatalogItem) -> Unit,
    notice: String? = null,
    switch: MicrogSwitch? = null,
) {
    val listFocus = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(listFocus)
            .focusGroup(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (notice != null) {
            item(key = "notice") { NoticeCard(notice) }
        }
        if (switch != null) {
            item(key = "switch") { SwitchCard(switch, onAction) }
        }
        sections.forEachIndexed { si, section ->
            if (section.title != null) {
                item(key = "h$si") { SectionHeader(section) }
            }
            items(
                count = section.items.size,
                key = { i -> "s${si}_$i" },
            ) { i ->
                AppRow(section.items[i], phases, progress, installed, installedPatches, onAction)
            }
        }
    }

    // On TV, pull focus into the list once it's composed so the D-pad starts on a row.
    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(150)
            runCatching { listFocus.requestFocus() }
        }
    }
}

@Composable
private fun InnerTabs(selected: Int, onSelect: (Int) -> Unit) {
    val index = selected.coerceIn(0, TAB_TITLES.lastIndex)
    TabRow(
        selectedTabIndex = index,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        TAB_TITLES.forEachIndexed { i, title ->
            Tab(
                selected = index == i,
                onClick = { onSelect(i) },
                text = {
                    Text(
                        title,
                        fontWeight = if (index == i) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onUpdate: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_vmpro_logo),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Update available",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Version $version — tap Update to get it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onUpdate) { Text("Update") }
        }
    }
}

@Composable
private fun NoticeCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun SwitchCard(switch: MicrogSwitch, onAction: (CatalogItem) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${switch.installedLabel} is installed", fontWeight = FontWeight.SemiBold)
                Text(
                    "Switch to ${switch.target.label}?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onAction(switch.target) }) { Text("Switch") }
        }
    }
}

@Composable
private fun SectionHeader(section: Section) {
    Row(
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        section.iconRes?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            section.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Whether the available build is newer than what's installed.
 *
 * A newer base app version is always an update. When the base version matches, we fall back
 * to the patch version: Android can't report the installed patch, so we compare the latest
 * available patch against the one VMPro recorded at install time (trusted only while its
 * base version still matches the installed one).
 */
private fun isUpdatable(
    item: CatalogItem,
    installedApp: InstalledApp?,
    installedPatches: Map<String, String>,
): Boolean {
    if (installedApp == null || item.asset == null) return false
    val details = item.details ?: return false
    val available = details.version
    if (isNewerVersion(available, installedApp.versionName)) return true

    // Same base version — look for a newer patch-only build we previously installed.
    if (available != installedApp.versionName) return false
    val availablePatch = details.patch ?: return false
    val raw = installedPatches[installedApp.packageName] ?: return false
    val sep = raw.indexOf('|')
    if (sep <= 0) return false
    val storedBase = raw.substring(0, sep)
    val storedPatch = raw.substring(sep + 1)
    return storedBase == installedApp.versionName && isNewerVersion(availablePatch, storedPatch)
}

/**
 * Resolve the installed app for a row, disambiguating shared-package items.
 *
 * GmsCore and MicroG RE install under one package id, so a plain package lookup makes every
 * shared row read the *other* product's version — e.g. GmsCore showing "Installed 6.14",
 * which is actually MicroG RE. We tell them apart by major-version family (GmsCore ships 0.x,
 * MicroG RE ships 6.x): the row counts as installed only when the installed major matches its
 * own available major. Non-exclusive rows keep the plain package match.
 */
private fun resolveInstalled(item: CatalogItem, installed: Map<String, InstalledApp>): InstalledApp? {
    val found = item.packages.firstNotNullOfOrNull { installed[it] } ?: return null
    if (!item.exclusive) return found
    return if (sameVersionFamily(item.details?.version, found.versionName)) found else null
}

@Composable
private fun AppRow(
    item: CatalogItem,
    phases: Map<String, DownloadPhase>,
    progress: Map<String, Int>,
    installed: Map<String, InstalledApp>,
    installedPatches: Map<String, String>,
    onAction: (CatalogItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val installedApp = resolveInstalled(item, installed)
    val updatable = isUpdatable(item, installedApp, installedPatches)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        // Light up the whole row for D-pad users when a control inside it has focus. The card
        // is a focus *group* (not one big focus target), so the D-pad steps through the row's
        // actual controls — the download/install button and the details chevron.
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .focusGroup()
            .then(
                if (focused) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp),
                ) else Modifier
            ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StateButton(item, phases, progress, installedApp, updatable, onAction)
                if (item.details != null) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Hide details" else "Show details",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            item.asset?.let { asset ->
                if (phases[asset.downloadUrl] == DownloadPhase.DOWNLOADING) {
                    Spacer(Modifier.size(10.dp))
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = (progress[asset.downloadUrl] ?: 0) / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                }
            }

            AnimatedVisibility(visible = expanded && item.details != null) {
                DetailsPanel(item, installedApp)
            }
        }
    }
}

@Composable
private fun DetailsPanel(item: CatalogItem, installedApp: InstalledApp?) {
    val context = LocalContext.current
    val d = item.details ?: return
    Column(Modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Spacer(Modifier.size(10.dp))
        DetailRow("Version", d.version ?: "—")
        d.patches?.let { DetailRow("Patches", it) }
        d.patch?.let { DetailRow("Patch version", it) }
        DetailRow("Compiled by", d.compiledBy)
        DetailRow("Size", d.size)
        d.lastUpdated?.let { DetailRow("Last updated", it) }
        installedApp?.let { DetailRow("Installed", it.versionName.ifBlank { "yes" }) }

        if (installedApp != null) {
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = { Downloader.uninstall(context, installedApp.packageName) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Uninstall")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StateButton(
    item: CatalogItem,
    phases: Map<String, DownloadPhase>,
    progress: Map<String, Int>,
    installedApp: InstalledApp?,
    updatable: Boolean,
    onAction: (CatalogItem) -> Unit,
) {
    val asset = item.asset
    if (asset == null) {
        FilledTonalButton(onClick = {}, enabled = false) { Text("N/A") }
        return
    }
    val phase = phases[asset.downloadUrl] ?: DownloadPhase.IDLE
    // Installed at the available version (or newer) — takes priority over a stale download
    // so the button flips to "Installed" as soon as the app is detected on the device.
    // (installedApp is already disambiguated for shared-package items by resolveInstalled.)
    val installedCurrent = asset.isApk && installedApp != null && !updatable
    when {
        phase == DownloadPhase.DOWNLOADING -> FilledTonalButton(
            onClick = {},
            enabled = false,
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text("${progress[asset.downloadUrl] ?: 0}%")
        }

        installedCurrent ->
            FilledTonalButton(onClick = {}, enabled = false) { Text("Installed") }

        phase == DownloadPhase.DONE && asset.isApk -> Button(onClick = { onAction(item) }) {
            Text("Install")
        }

        phase == DownloadPhase.DONE && !asset.isApk ->
            FilledTonalButton(onClick = {}, enabled = false) { Text("Downloaded") }

        // Installed but a newer build is available.
        asset.isApk && installedApp != null && updatable ->
            Button(
                onClick = { onAction(item) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                ),
            ) { Text("Update") }

        else -> FilledTonalButton(onClick = { onAction(item) }) { Text("Download") }
    }
}
