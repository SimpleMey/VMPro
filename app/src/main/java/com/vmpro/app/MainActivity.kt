package com.vmpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import com.vmpro.app.data.isNewerVersion
import com.vmpro.app.ui.AboutScreen
import com.vmpro.app.ui.CatalogItem
import com.vmpro.app.ui.InstalledApp
import com.vmpro.app.ui.ManagerViewModel
import com.vmpro.app.ui.Section
import com.vmpro.app.ui.TAB_APPS
import com.vmpro.app.ui.TAB_MICROG
import com.vmpro.app.ui.TAB_MODULES
import com.vmpro.app.ui.TAB_TITLES
import com.vmpro.app.ui.TabState
import com.vmpro.app.ui.VmproTheme
import com.vmpro.app.util.Downloader
import com.vmpro.app.util.DownloadPhase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VmproTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
    } else {
        ManagerScreen(onOpenAbout = { showAbout = true })
    }
}

private data class NavDest(val tab: Int, val icon: ImageVector)

private val NAV_DESTS = listOf(
    NavDest(TAB_APPS, Icons.Filled.Apps),
    NavDest(TAB_MICROG, Icons.Filled.Security),
    NavDest(TAB_MODULES, Icons.Filled.Extension),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerScreen(
    onOpenAbout: () -> Unit,
    viewModel: ManagerViewModel = viewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val phases by viewModel.downloadPhases.collectAsStateWithLifecycle()
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val conflict by viewModel.conflict.collectAsStateWithLifecycle()
    val state = states[selectedTab] ?: TabState.Loading

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
            TopAppBar(
                title = { Text("VMPro", fontWeight = FontWeight.Bold) },
                actions = {
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
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NAV_DESTS.forEach { dest ->
                    NavigationBarItem(
                        selected = selectedTab == dest.tab,
                        onClick = { viewModel.selectTab(dest.tab) },
                        icon = { Icon(dest.icon, contentDescription = TAB_TITLES[dest.tab]) },
                        label = { Text(TAB_TITLES[dest.tab]) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
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
                installed = installed,
                onAction = viewModel::onAction,
            )
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
    installed: Map<String, InstalledApp>,
    onAction: (CatalogItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sections.forEachIndexed { si, section ->
            if (section.title != null) {
                item(key = "h$si") { SectionHeader(section) }
            }
            items(
                count = section.items.size,
                key = { i -> "s${si}_$i" },
            ) { i ->
                AppRow(section.items[i], phases, installed, onAction)
            }
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

@Composable
private fun AppRow(
    item: CatalogItem,
    phases: Map<String, DownloadPhase>,
    installed: Map<String, InstalledApp>,
    onAction: (CatalogItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val installedApp = item.packages.firstNotNullOfOrNull { installed[it] }
    val updatable = installedApp != null && item.asset != null &&
        isNewerVersion(item.details?.version, installedApp.versionName)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
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
                    // Exclusive items can't show a terminal "Installed" button (shared package),
                    // so surface the installed state as a small badge instead.
                    if (item.exclusive && installedApp != null) {
                        Text(
                            "● Installed ${installedApp.versionName}".trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StateButton(item, phases, installedApp, updatable, onAction)
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
        d.patch?.let { DetailRow("Patch version", it) }
        DetailRow("Compiled by", d.compiledBy)
        DetailRow("Size", d.size)
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
    val installedCurrent = !item.exclusive && asset.isApk && installedApp != null && !updatable
    when {
        phase == DownloadPhase.DOWNLOADING -> FilledTonalButton(onClick = {}, enabled = false) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        installedCurrent ->
            FilledTonalButton(onClick = {}, enabled = false) { Text("Installed") }

        phase == DownloadPhase.DONE && asset.isApk -> Button(onClick = { onAction(item) }) {
            Text("Install")
        }

        phase == DownloadPhase.DONE && !asset.isApk ->
            FilledTonalButton(onClick = {}, enabled = false) { Text("Downloaded") }

        // Installed but a newer build is available.
        !item.exclusive && asset.isApk && installedApp != null && updatable ->
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
