package com.vmpro.app.ui

import android.app.Application
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vmpro.app.BuildConfig
import com.vmpro.app.analytics.Analytics
import com.vmpro.app.data.APP_CATALOG
import com.vmpro.app.data.Asset
import com.vmpro.app.data.CacheMissException
import com.vmpro.app.data.DiskCache
import com.vmpro.app.data.GithubRepository
import com.vmpro.app.data.InstalledPatchStore
import com.vmpro.app.data.J_HC
import com.vmpro.app.data.MICROG_CATALOG
import com.vmpro.app.data.MODULE_CATALOG
import com.vmpro.app.data.MORPHE_BUILDS
import com.vmpro.app.data.MorpheBuilds
import com.vmpro.app.data.PHONE_DIRECT
import com.vmpro.app.data.Release
import com.vmpro.app.data.TV_CATALOG
import com.vmpro.app.data.formatBytes
import com.vmpro.app.data.isNewerVersion
import com.vmpro.app.data.parsePatchVersion
import com.vmpro.app.data.sameVersionFamily
import com.vmpro.app.data.versionOf
import com.vmpro.app.util.DownloadController
import com.vmpro.app.util.DownloadPhase
import com.vmpro.app.data.VtOutcome
import com.vmpro.app.util.Downloader
import com.vmpro.app.util.ShizukuInstaller
import com.vmpro.app.util.VtPrefs
import com.vmpro.app.util.fileSha256
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Inner tabs shown inside the "Apps" bottom destination.
const val TAB_APPS = 0
const val TAB_MICROG = 1
const val TAB_MODULES = 2
val TAB_TITLES = listOf("Apps", "MicroG", "Modules")

// Separate bottom destination.
const val TAB_TV = 3

/** Extra info shown in a row's expandable dropdown. */
data class AppDetails(
    val version: String?,
    val patch: String?,
    val compiledBy: String,
    val size: String,
    val lastUpdated: String?,
    /** Patch brand shown in the Apps tab (e.g. "Morphe", "Piko"); null elsewhere. */
    val patches: String? = null,
)

/** One attribution shown on the app detail page: who built the APK, or whose patches it uses. */
data class SourceRef(
    val role: String,
    val name: String,
    val repo: String? = null,
    val url: String? = null,
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
    /** Where this build and its patches come from, shown on the detail page. */
    val sources: List<SourceRef> = emptyList(),
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

    private val repository = GithubRepository(DiskCache(app.cacheDir))
    private val pm: PackageManager = app.packageManager
    val downloads = DownloadController(app).also { it.register() }

    private val _selectedTab = MutableStateFlow(TAB_APPS)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _states = MutableStateFlow<Map<Int, TabState>>(emptyMap())
    val states: StateFlow<Map<Int, TabState>> = _states.asStateFlow()

    /** pkg -> installed app (refreshed on launch and on resume). */
    private val _installed = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())
    val installed: StateFlow<Map<String, InstalledApp>> = _installed.asStateFlow()

    /**
     * pkg -> "baseVersion|patchVersion" for builds VMPro installed. Lets the UI detect a
     * patch-only update (same base app version, newer patches), which Android's
     * PackageManager can't reveal on its own. Seeded from disk, updated on each install.
     */
    private val _installedPatches = MutableStateFlow(InstalledPatchStore.all(app))
    val installedPatches: StateFlow<Map<String, String>> = _installedPatches.asStateFlow()

    /** Non-null while a same-package install conflict is awaiting the user's choice. */
    private val _conflict = MutableStateFlow<ConflictInfo?>(null)
    val conflict: StateFlow<ConflictInfo?> = _conflict.asStateFlow()

    /** Package ids that appear in more than one catalog item (mutually exclusive installs). */
    private val sharedPackages: Set<String> =
        (APP_CATALOG.mapNotNull { it.packages.firstOrNull() } +
            MICROG_CATALOG.mapNotNull { it.packages.firstOrNull() })
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    private var morpheBuilds: MorpheBuilds? = null
    private var jhcReleases: List<Release>? = null

    /** Newer VMPro version string (e.g. "4.4") when an app update is available, else null. */
    private val _updateVersion = MutableStateFlow<String?>(null)
    val updateVersion: StateFlow<String?> = _updateVersion.asStateFlow()

    init {
        refreshInstalled()
        Analytics.tabView(titleFor(TAB_APPS))
        load(TAB_APPS)
        checkForUpdate()
    }

    private fun titleFor(index: Int) = if (index == TAB_TV) "TV" else TAB_TITLES[index]

    /** Compare the latest VMPro release on GitHub against this build's version. */
    private fun checkForUpdate() {
        viewModelScope.launch {
            runCatching {
                val releases = repository.fetchReleases("SimpleMey", "VMPro", perPage = 5)
                val latest = releases.firstOrNull()?.tag?.removePrefix("v")?.trim()
                if (latest != null && isNewerVersion(latest, BuildConfig.VERSION_NAME)) {
                    _updateVersion.value = latest
                }
            }
        }
    }

    fun selectTab(index: Int) {
        if (_selectedTab.value != index) Analytics.tabView(titleFor(index))
        _selectedTab.value = index
        if (_states.value[index] !is TabState.Success) load(index)
    }

    fun refresh() {
        when (_selectedTab.value) {
            TAB_APPS -> morpheBuilds = null
            TAB_MODULES -> jhcReleases = null
        }
        refreshInstalled()
        load(_selectedTab.value)
    }

    val downloadPhases get() = downloads.phases
    val downloadProgress get() = downloads.progress

    fun onAction(item: CatalogItem) {
        val asset = item.asset ?: return
        when (downloads.phaseOf(asset)) {
            DownloadPhase.DONE -> if (asset.isApk) attemptInstall(item)
            DownloadPhase.DOWNLOADING -> Unit
            else -> downloads.download(asset)
        }
    }

    /**
     * Check the downloaded APK on VirusTotal using the user's own API key (set in Settings) and
     * show the result in a toast. It is a hash lookup, not an upload, so nothing is sent per build;
     * a freshly built APK that no one has scanned yet comes back as "not on VirusTotal yet".
     */
    fun verifyOnVirusTotal(item: CatalogItem) {
        val asset = item.asset ?: return
        val ctx = getApplication<Application>()
        val key = VtPrefs.key(ctx)
        if (key.isBlank()) {
            Toast.makeText(
                ctx, "Add your VirusTotal API key in Settings to check files.", Toast.LENGTH_LONG,
            ).show()
            return
        }
        val uri = downloads.localUri(asset.downloadUrl)
        if (uri == null) {
            Toast.makeText(ctx, "Download the app first, then verify.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(ctx, "Checking on VirusTotal...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            val hash = withContext(Dispatchers.IO) { fileSha256(ctx, uri) }
            if (hash == null) {
                Toast.makeText(ctx, "Could not read the APK.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val outcome = runCatching { repository.virusTotalLookup(key, hash) }.getOrNull()
            val msg = when (outcome) {
                is VtOutcome.Found ->
                    if (outcome.flagged == 0)
                        "VirusTotal: clean. 0 of ${outcome.total} engines flagged it."
                    else
                        "VirusTotal: ${outcome.flagged} of ${outcome.total} engines flagged this file."
                VtOutcome.NotFound ->
                    "This exact file is not on VirusTotal yet. Freshly built APKs often are not."
                VtOutcome.BadKey -> "VirusTotal rejected the key. Check it in Settings."
                VtOutcome.RateLimited -> "VirusTotal rate limit reached. Try again shortly."
                is VtOutcome.Error -> "VirusTotal check failed (code ${outcome.code})."
                null -> "Could not reach VirusTotal. Check your connection."
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Install, but if installing would replace the *other* app sharing this package, ask first. */
    private fun attemptInstall(item: CatalogItem) {
        val asset = item.asset ?: return
        val installedPkg = item.packages.firstOrNull { _installed.value.containsKey(it) }
        // Only a real switch between the two products sharing this package (GmsCore <-> MicroG RE)
        // is a conflict. Updating the same product in place is not — Android just updates it.
        val switchingProduct = item.exclusive && installedPkg != null &&
            !sameVersionFamily(item.details?.version, _installed.value[installedPkg]?.versionName)
        if (switchingProduct) {
            _conflict.value = ConflictInfo(installedPkg!!, asset, item.label)
        } else {
            recordPatch(item)
            doInstall(asset)
        }
    }

    /**
     * Install silently via Shizuku when the user enabled it and Shizuku is available; otherwise
     * launch the normal system installer. On a Shizuku failure we surface the error and leave the
     * normal installer as the manual retry (the user can just tap Install again).
     */
    private fun doInstall(asset: Asset) {
        val ctx = getApplication<Application>()
        if (VtPrefs.shizukuInstall(ctx) && ShizukuInstaller.available()) {
            val uri = downloads.localUri(asset.downloadUrl)
            if (uri == null) {
                downloads.install(asset)
                return
            }
            Toast.makeText(ctx, "Installing with Shizuku...", Toast.LENGTH_SHORT).show()
            viewModelScope.launch {
                val (ok, msg) = ShizukuInstaller.install(ctx, uri)
                Toast.makeText(
                    ctx,
                    if (ok) "Installed." else "Shizuku install failed: $msg",
                    if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
                refreshInstalled()
            }
        } else {
            downloads.install(asset)
        }
    }

    /**
     * Remember the patch version we're about to install for this item's package, so a later
     * patch-only release is recognised as an update. Recorded at install-launch (we get no
     * success callback from the system installer); harmless if the user cancels — the next
     * real install corrects it.
     */
    private fun recordPatch(item: CatalogItem) {
        val pkg = item.packages.firstOrNull() ?: return
        val base = item.details?.version ?: return
        val patch = item.details?.patch ?: return
        InstalledPatchStore.record(getApplication(), pkg, base, patch)
        _installedPatches.update { it + (pkg to "$base|$patch") }
    }

    fun uninstallConflict() {
        _conflict.value?.let { Downloader.uninstall(getApplication(), it.packageName) }
        _conflict.value = null
    }

    fun installAnyway() {
        _conflict.value?.let { doInstall(it.asset) }
        _conflict.value = null
    }

    fun dismissConflict() {
        _conflict.value = null
    }

    /** Re-query PackageManager for every catalog package. Call on launch and on resume. */
    fun refreshInstalled() {
        val all = (APP_CATALOG.flatMap { it.packages } +
            MICROG_CATALOG.flatMap { it.packages } +
            PHONE_DIRECT.flatMap { it.packages } +
            TV_CATALOG.flatMap { it.packages }).distinct()
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
        if (tab == TAB_APPS) {
            loadApps()
            return
        }
        _states.update { it + (tab to TabState.Loading) }
        viewModelScope.launch {
            val state = try {
                TabState.Success(
                    when (tab) {
                        TAB_MICROG -> loadMicroG()
                        TAB_MODULES -> loadModules()
                        TAB_TV -> loadTv()
                        else -> emptyList()
                    }
                )
            } catch (e: Exception) {
                TabState.Error(e.message ?: "Failed to load.")
            }
            _states.update { it + (tab to state) }
        }
    }

    /**
     * Apps tab load with stale-while-revalidate: paint instantly from the previous launch's
     * cached responses (no spinner), then refresh from the network and repaint. The very first
     * launch (nothing cached yet) shows the spinner and then the fetched list, as before.
     */
    private fun loadApps() {
        viewModelScope.launch {
            val alreadyShown = _states.value[TAB_APPS] is TabState.Success
            // Phase 1: instant paint from disk cache, when we have usable cached data.
            val cached = runCatching { buildApps(cacheFirst = true) }.getOrNull()
            if (cached != null && !alreadyShown) {
                _states.update { it + (TAB_APPS to TabState.Success(cached)) }
            } else if (!alreadyShown) {
                _states.update { it + (TAB_APPS to TabState.Loading) }
            }
            // Phase 2: fresh from the network.
            val fresh = try {
                TabState.Success(buildApps(cacheFirst = false))
            } catch (e: Exception) {
                // A refresh failure keeps the cached list on screen rather than flipping to error.
                if (cached != null) return@launch
                TabState.Error(e.message ?: "Failed to load.")
            }
            _states.update { it + (TAB_APPS to fresh) }
        }
    }

    /** Build the Apps sections. [cacheFirst] reads only the disk cache (for the instant paint). */
    private suspend fun buildApps(cacheFirst: Boolean): List<Section> = coroutineScope {
        val builds = if (cacheFirst) {
            repository.fetchMorpheBuilds(MORPHE_BUILDS, cacheFirst = true)
        } else {
            morpheBuilds ?: repository.fetchMorpheBuilds(MORPHE_BUILDS).also { morpheBuilds = it }
        }
        // Nothing usable cached yet -> signal "no instant paint" so the caller shows the spinner.
        if (cacheFirst && (builds.release == null || builds.entries.isEmpty())) throw CacheMissException()

        // Resolve the direct GitHub apps (NewTube) in parallel with assembling the Morphe rows.
        val directDeferred = PHONE_DIRECT.map { entry -> async { loadReleaseItem(entry, cacheFirst) } }
        val morpheItems = APP_CATALOG.map { entry ->
            val resolved = repository.resolveMorphe(builds, entry.appName, entry.source)
            val asset = resolved?.asset
            val version = resolved?.version
            val details = resolved?.let {
                AppDetails(
                    version = version,
                    patch = null,
                    compiledBy = "Morphe",
                    size = formatBytes(it.asset.sizeBytes),
                    lastUpdated = it.release.publishedAt.ifBlank { null },
                    patches = patchBrandFor(entry.source),
                )
            }
            val builtUrl = resolved?.release?.htmlUrl
                ?: "https://github.com/${MORPHE_BUILDS.owner}/${MORPHE_BUILDS.repo}/releases"
            val sources = buildList {
                add(SourceRef("Built by", "Morphe auto-builds",
                    "${MORPHE_BUILDS.owner}/${MORPHE_BUILDS.repo}", builtUrl))
                patchRepoFor(entry.source)?.let { (slug, url) ->
                    add(SourceRef("Patches", patchBrandFor(entry.source), slug, url))
                }
            }
            CatalogItem(
                label = entry.label,
                iconRes = entry.iconRes,
                asset = asset,
                subtitle = subtitleFor(asset, version, "No APK available"),
                details = details,
                packages = entry.packages,
                exclusive = entry.packages.firstOrNull() in sharedPackages,
                sources = sources,
            )
        }
        val directItems = directDeferred.awaitAll()
        listOf(Section(title = null, iconRes = null, items = morpheItems + directItems))
    }

    /** Human patch-brand name for a Morphe-Builds source key. */
    private fun patchBrandFor(source: String): String = when (source) {
        "morphe" -> "Morphe"
        "piko-newx", "piko" -> "Piko"
        "revanced-anddea" -> "Anddea"
        else -> source.replaceFirstChar { it.uppercase() }
    }

    /** "owner/repo" + web URL of the patch set behind a Morphe-Builds / module source key. */
    private fun patchRepoFor(source: String): Pair<String, String>? = when (source) {
        "morphe" -> "MorpheApp/morphe-patches" to "https://github.com/MorpheApp/morphe-patches"
        "piko-newx", "piko" -> "crimera/piko-newx" to "https://github.com/crimera/piko-newx"
        "revanced-anddea" -> "anddea/revanced-patches" to "https://github.com/anddea/revanced-patches"
        else -> null
    }

    /** Resolve one app straight from its own GitHub release (TV apps and direct phone apps). */
    private suspend fun loadReleaseItem(
        entry: com.vmpro.app.data.TvEntry,
        cacheFirst: Boolean = false,
    ): CatalogItem {
        val resolved = repository.resolveTv(entry, cacheFirst)
        val asset = resolved?.asset
        val version = resolved?.release?.tag
            ?.removePrefix("v")?.trimEnd('s')?.takeIf { it.isNotBlank() }
        val details = resolved?.let {
            AppDetails(
                version = version,
                patch = null,
                compiledBy = entry.owner,
                size = formatBytes(it.asset.sizeBytes),
                lastUpdated = it.release.publishedAt.ifBlank { null },
            )
        }
        val repoUrl = resolved?.release?.htmlUrl
            ?: "https://github.com/${entry.owner}/${entry.repo}/releases"
        return CatalogItem(
            label = entry.label,
            iconRes = entry.iconRes,
            asset = asset,
            subtitle = subtitleFor(asset, version, entry.subtitle),
            details = details,
            packages = entry.packages,
            exclusive = false,
            sources = listOf(SourceRef("Source", entry.owner, "${entry.owner}/${entry.repo}", repoUrl)),
        )
    }

    /** Modules tab — Magisk/KernelSU .zip modules, still from j-hc. */
    private suspend fun loadModules(): List<Section> {
        val releases = jhcReleases ?: repository.fetchReleases(J_HC).also { jhcReleases = it }
        val items = MODULE_CATALOG.map { entry ->
            val resolved = repository.resolveModule(releases, entry)
            val asset = resolved?.asset
            val version = asset?.let { versionOf(it.name)?.removePrefix("v") }
            val details = resolved?.let {
                AppDetails(
                    version = version,
                    patch = parsePatchVersion(it.release.body, entry.variant),
                    compiledBy = J_HC.owner,
                    size = formatBytes(it.asset.sizeBytes),
                    lastUpdated = it.release.publishedAt.ifBlank { null },
                )
            }
            val relUrl = resolved?.release?.htmlUrl
                ?: "https://github.com/${J_HC.owner}/${J_HC.repo}/releases"
            val sources = buildList {
                add(SourceRef("Source", J_HC.owner, "${J_HC.owner}/${J_HC.repo}", relUrl))
                patchRepoFor(entry.variant)?.let { (slug, url) ->
                    add(SourceRef("Patches", patchBrandFor(entry.variant), slug, url))
                }
            }
            CatalogItem(
                label = entry.label,
                iconRes = entry.iconRes,
                asset = asset,
                subtitle = subtitleFor(asset, version, "No module available"),
                details = details,
                packages = emptyList(),
                exclusive = false,
                sources = sources,
            )
        }
        return listOf(Section(title = null, iconRes = null, items = items))
    }

    /** MicroG tab — GmsCore + MicroG RE from their own repos (resolved in parallel). */
    private suspend fun loadMicroG(): List<Section> = coroutineScope {
        val items = MICROG_CATALOG.map { entry ->
            async {
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
                        lastUpdated = it.release.publishedAt.ifBlank { null },
                    )
                }
                val relUrl = resolved?.release?.htmlUrl
                    ?: "https://github.com/${entry.owner}/${entry.repo}/releases"
                CatalogItem(
                    label = entry.label,
                    iconRes = entry.iconRes,
                    asset = asset,
                    subtitle = subtitleFor(asset, version, "No APK available"),
                    details = details,
                    packages = entry.packages,
                    exclusive = entry.packages.firstOrNull() in sharedPackages,
                    sources = listOf(SourceRef("Source", entry.owner, "${entry.owner}/${entry.repo}", relUrl)),
                )
            }
        }.awaitAll()
        listOf(Section(title = null, iconRes = null, items = items))
    }

    /** TV tab — Android TV apps from their own GitHub release pages (resolved in parallel). */
    private suspend fun loadTv(): List<Section> = coroutineScope {
        val items = TV_CATALOG.map { entry -> async { loadReleaseItem(entry) } }.awaitAll()
        listOf(Section(title = null, iconRes = null, items = items))
    }

    private fun subtitleFor(asset: Asset?, version: String?, emptyLabel: String): String {
        if (asset == null) return emptyLabel
        val v = version?.let { "$it · " } ?: ""
        return "$v${formatBytes(asset.sizeBytes)}"
    }

    override fun onCleared() {
        downloads.unregister()
        super.onCleared()
    }
}
