package com.vmpro.app.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vmpro.app.analytics.Analytics
import com.vmpro.app.data.APP_CATALOG
import com.vmpro.app.data.Asset
import com.vmpro.app.data.GithubRepository
import com.vmpro.app.data.J_HC
import com.vmpro.app.data.MICROG_CATALOG
import com.vmpro.app.data.Project
import com.vmpro.app.data.Release
import com.vmpro.app.data.formatBytes
import com.vmpro.app.data.parsePatchVersion
import com.vmpro.app.data.versionOf
import com.vmpro.app.util.DownloadController
import com.vmpro.app.util.DownloadPhase
import com.vmpro.app.util.Downloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val TAB_APPS = 0
const val TAB_MICROG = 1
const val TAB_MODULES = 2
val TAB_TITLES = listOf("Apps", "MicroG", "Modules")

/** Extra info shown in a row's expandable dropdown. */
data class AppDetails(
    val version: String?,
    val patch: String?,
    val compiledBy: String,
    val size: String,
)

/** A single resolved row: an app/module and the file to download (if any). */
data class CatalogItem(
    val label: String,
    @DrawableRes val iconRes: Int,
    val asset: Asset?,
    val subtitle: String,
    val details: AppDetails?,
    /** Candidate package ids for install detection (empty for modules). */
    val packages: List<String>,
    /** True when this item's package is shared with another (mutually exclusive install). */
    val exclusive: Boolean = false,
)

/** Raised when installing would collide with an app already on the device. */
data class ConflictInfo(val packageName: String, val asset: Asset, val label: String)

/** A group of rows, optionally under a project heading. */
data class Section(
    val title: String?,
    @DrawableRes val iconRes: Int?,
    val items: List<CatalogItem>,
)

/** An installed package the manager knows about. */
data class InstalledApp(val packageName: String, val versionName: String)

sealed interface TabState {
    data object Loading : TabState
    data class Success(val sections: List<Section>) : TabState
    data class Error(val message: String) : TabState
}

class ManagerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = GithubRepository()
    private val pm: PackageManager = app.packageManager
    val downloads = DownloadController(app).also { it.register() }

    private val _selectedTab = MutableStateFlow(TAB_APPS)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _states = MutableStateFlow<Map<Int, TabState>>(emptyMap())
    val states: StateFlow<Map<Int, TabState>> = _states.asStateFlow()

    /** pkg -> installed app (refreshed on launch and on resume). */
    private val _installed = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())
    val installed: StateFlow<Map<String, InstalledApp>> = _installed.asStateFlow()

    /** Non-null while a same-package install conflict is awaiting the user's choice. */
    private val _conflict = MutableStateFlow<ConflictInfo?>(null)
    val conflict: StateFlow<ConflictInfo?> = _conflict.asStateFlow()

    /** Package ids that appear in more than one catalog item (mutually exclusive installs). */
    private val sharedPackages: Set<String> =
        (APP_CATALOG.mapNotNull { it.packages.firstOrNull() } +
            MICROG_CATALOG.mapNotNull { it.packages.firstOrNull() })
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    private var jhcReleases: List<Release>? = null

    init {
        refreshInstalled()
        Analytics.tabView(TAB_TITLES[TAB_APPS])
        load(TAB_APPS)
    }

    fun selectTab(index: Int) {
        if (_selectedTab.value != index) Analytics.tabView(TAB_TITLES[index])
        _selectedTab.value = index
        if (_states.value[index] !is TabState.Success) load(index)
    }

    fun refresh() {
        if (_selectedTab.value != TAB_MICROG) jhcReleases = null
        refreshInstalled()
        load(_selectedTab.value)
    }

    val downloadPhases get() = downloads.phases

    fun onAction(item: CatalogItem) {
        val asset = item.asset ?: return
        when (downloads.phaseOf(asset)) {
            DownloadPhase.DONE -> if (asset.isApk) attemptInstall(item)
            DownloadPhase.DOWNLOADING -> Unit
            else -> downloads.download(asset)
        }
    }

    /** Install, but if a conflicting same-package app is present, ask the user first. */
    private fun attemptInstall(item: CatalogItem) {
        val asset = item.asset ?: return
        val installedPkg = item.packages.firstOrNull { _installed.value.containsKey(it) }
        if (item.exclusive && installedPkg != null) {
            _conflict.value = ConflictInfo(installedPkg, asset, item.label)
        } else {
            downloads.install(asset)
        }
    }

    fun uninstallConflict() {
        _conflict.value?.let { Downloader.uninstall(getApplication(), it.packageName) }
        _conflict.value = null
    }

    fun installAnyway() {
        _conflict.value?.let { downloads.install(it.asset) }
        _conflict.value = null
    }

    fun dismissConflict() {
        _conflict.value = null
    }

    /** Re-query PackageManager for every catalog package. Call on launch and on resume. */
    fun refreshInstalled() {
        val all = (APP_CATALOG.flatMap { it.packages } + MICROG_CATALOG.flatMap { it.packages })
            .distinct()
        val map = HashMap<String, InstalledApp>()
        for (pkg in all) {
            try {
                val info = pm.getPackageInfo(pkg, 0)
                map[pkg] = InstalledApp(pkg, info.versionName.orEmpty())
            } catch (_: PackageManager.NameNotFoundException) {
                // not installed
            }
        }
        _installed.value = map
    }

    private fun load(tab: Int) {
        _states.update { it + (tab to TabState.Loading) }
        viewModelScope.launch {
            val state = try {
                when (tab) {
                    TAB_MICROG -> TabState.Success(loadMicroG())
                    else -> TabState.Success(loadAppsOrModules(wantApk = tab == TAB_APPS))
                }
            } catch (e: Exception) {
                TabState.Error(e.message ?: "Failed to load.")
            }
            _states.update { it + (tab to state) }
        }
    }

    private suspend fun loadAppsOrModules(wantApk: Boolean): List<Section> {
        val releases = jhcReleases ?: repository.fetchReleases(J_HC).also { jhcReleases = it }
        return Project.entries.map { project ->
            val items = APP_CATALOG.filter { it.project == project }.map { entry ->
                val resolved = repository.resolveApp(releases, entry, wantApk)
                val asset = resolved?.asset
                val version = asset?.let { versionOf(it.name)?.removePrefix("v") }
                val details = resolved?.let {
                    AppDetails(
                        version = version,
                        patch = parsePatchVersion(it.release.body, entry.variant),
                        compiledBy = J_HC.owner,
                        size = formatBytes(it.asset.sizeBytes),
                    )
                }
                val packages = if (wantApk) entry.packages else emptyList()
                CatalogItem(
                    label = entry.label,
                    iconRes = entry.iconRes,
                    asset = asset,
                    subtitle = subtitleFor(asset, version, wantApk),
                    details = details,
                    packages = packages,
                    exclusive = packages.firstOrNull() in sharedPackages,
                )
            }
            Section(project.label, project.iconRes, items)
        }
    }

    private suspend fun loadMicroG(): List<Section> {
        val items = MICROG_CATALOG.map { entry ->
            val resolved = repository.resolveMicroG(entry)
            val asset = resolved?.asset
            val version = resolved?.release?.tag?.removePrefix("v")
                ?: asset?.let { versionOf(it.name)?.removePrefix("v") }
            val details = resolved?.let {
                AppDetails(
                    version = version,
                    patch = null,
                    compiledBy = entry.owner,
                    size = formatBytes(it.asset.sizeBytes),
                )
            }
            CatalogItem(
                label = entry.label,
                iconRes = entry.iconRes,
                asset = asset,
                subtitle = subtitleFor(asset, version, wantApk = true),
                details = details,
                packages = entry.packages,
                exclusive = entry.packages.firstOrNull() in sharedPackages,
            )
        }
        return listOf(Section(title = null, iconRes = null, items = items))
    }

    private fun subtitleFor(asset: Asset?, version: String?, wantApk: Boolean): String {
        if (asset == null) return if (wantApk) "No APK available" else "No module available"
        val v = version?.let { "$it · " } ?: ""
        return "$v${formatBytes(asset.sizeBytes)}"
    }

    override fun onCleared() {
        downloads.unregister()
        super.onCleared()
    }
}
