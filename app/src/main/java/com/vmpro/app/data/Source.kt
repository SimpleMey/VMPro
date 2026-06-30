package com.vmpro.app.data

/** A GitHub repository whose releases this manager surfaces. */
data class Source(
    val title: String,
    val subtitle: String,
    val owner: String,
    val repo: String,
) {
    val htmlUrl: String get() = "https://github.com/$owner/$repo/releases"
}

/**
 * All sources are publicly available GitHub release pages. The manager only links to
 * the files published there — it does not host or modify any of them.
 */
val SOURCES: List<Source> = listOf(
    Source(
        title = "ReVanced builds & modules",
        subtitle = "j-hc / revanced-magisk-module",
        owner = "j-hc",
        repo = "revanced-magisk-module",
    ),
    Source(
        title = "MicroG · GmsCore",
        subtitle = "ReVanced / GmsCore",
        owner = "ReVanced",
        repo = "GmsCore",
    ),
    Source(
        title = "MicroG RE",
        subtitle = "MorpheApp / MicroG-RE",
        owner = "MorpheApp",
        repo = "MicroG-RE",
    ),
)
