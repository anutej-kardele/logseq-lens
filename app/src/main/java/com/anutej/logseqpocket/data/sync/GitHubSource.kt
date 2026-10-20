package com.anutej.logseqpocket.data.sync

/**
 * Where the export lives on GitHub. Not secret: it's stored in plain settings; only the token is encrypted.
 * Each person points the app at their own private repo.
 */
data class GitHubSourceConfig(
    val owner: String,
    val repo: String,
    val branch: String,
    val path: String,
) {
    val repoSlug: String get() = "$owner/$repo"
    /** Short display form, e.g. "GitHub anutej-kardele/logseq-backup@master". */
    val label: String get() = "GitHub $owner/$repo@$branch"
    /** Identifies the exact file; stored with the dataset so ETags are only reused for the same source. */
    val key: String get() = "$owner/$repo@$branch:$path"
    val folder: String get() = path.substringBeforeLast('/', "")
    val fileName: String get() = path.substringAfterLast('/')

    /** A user-facing problem with this source, or null when it's usable. */
    fun validate(): String? = when {
        !NAME_RE.matches(owner) -> "Owner can only contain letters, numbers, '.', '-' and '_'."
        !NAME_RE.matches(repo) -> "Repository name can only contain letters, numbers, '.', '-' and '_'."
        branch.isBlank() || branch.contains("..") || branch.any { it.isWhitespace() } -> "Enter a valid branch name."
        path.isBlank() || path.startsWith("/") || path.split('/').any { it == ".." || it.isEmpty() } ->
            "Enter the file path inside the repo, e.g. data/mobile/activity.json."
        !path.endsWith(".json", ignoreCase = true) -> "The file must be a .json export."
        else -> null
    }

    companion object {
        private val NAME_RE = Regex("^[A-Za-z0-9._-]+$")
    }
}

/** The original owner's repository; also the starting point for everyone else's form. */
val DEFAULT_SOURCE = GitHubSourceConfig(
    owner = "anutej-kardele",
    repo = "logseq-backup",
    branch = "master",
    path = "data/mobile/activity.json",
)

/** What a pasted link told us. Missing parts are discovered from GitHub (default branch, .json files). */
data class ParsedGitHubLink(
    val owner: String,
    val repo: String,
    val branch: String? = null,
    /** A file path (from a blob/raw link) or a folder (from a tree link, see [isFolder]). */
    val path: String? = null,
    val isFolder: Boolean = false,
)

private val NAME = "[A-Za-z0-9._-]+"
private val SSH_RE = Regex("^git@github\\.com:($NAME)/($NAME?)(?:\\.git)?$")
private val SHORT_RE = Regex("^($NAME)/($NAME?)(?:\\.git)?$")
private val WEB_RE = Regex("^(?:https?://)?(?:www\\.)?github\\.com/(.+)$", RegexOption.IGNORE_CASE)
private val RAW_RE = Regex("^(?:https?://)?raw\\.githubusercontent\\.com/(.+)$", RegexOption.IGNORE_CASE)

/**
 * Understands the links people actually copy: the clone link (`https://github.com/o/r.git`), the SSH clone
 * link, `o/r`, a repo page, a file page (`/blob/<branch>/<path>`), a folder page (`/tree/<branch>/<dir>`)
 * and a raw file link. Returns null for anything else. Branch names containing '/' can't be told apart
 * from the path here, so the form keeps every field editable.
 */
fun parseGitHubLink(text: String): ParsedGitHubLink? {
    val t = text.trim().substringBefore('#').substringBefore('?').trimEnd('/')
    if (t.isEmpty()) return null
    SSH_RE.matchEntire(t)?.let { return repoOnly(it.groupValues[1], it.groupValues[2]) }
    RAW_RE.matchEntire(t)?.let { m ->
        val seg = m.groupValues[1].split('/').filter { it.isNotEmpty() }
        if (seg.size < 2) return null
        return ParsedGitHubLink(
            owner = seg[0], repo = seg[1].removeSuffix(".git"),
            branch = seg.getOrNull(2),
            path = seg.drop(3).joinToString("/").ifEmpty { null },
        ).takeIf(::validNames)
    }
    WEB_RE.matchEntire(t)?.let { m ->
        val seg = m.groupValues[1].split('/').filter { it.isNotEmpty() }
        if (seg.size < 2) return null
        val owner = seg[0]
        val repo = seg[1].removeSuffix(".git")
        val kind = seg.getOrNull(2)
        if ((kind == "blob" || kind == "tree") && seg.size >= 4) {
            val rest = seg.drop(4).joinToString("/").ifEmpty { null }
            return ParsedGitHubLink(owner, repo, branch = seg[3], path = rest, isFolder = kind == "tree")
                .takeIf(::validNames)
        }
        return repoOnly(owner, repo)
    }
    SHORT_RE.matchEntire(t)?.let { return repoOnly(it.groupValues[1], it.groupValues[2]) }
    return null
}

private fun repoOnly(owner: String, repo: String) = ParsedGitHubLink(owner, repo.removeSuffix(".git")).takeIf(::validNames)

private fun validNames(p: ParsedGitHubLink): Boolean =
    p.owner.matches(Regex("^$NAME$")) && p.repo.matches(Regex("^$NAME$")) && p.repo.isNotEmpty()

/** Repo housekeeping files that are JSON but never a graph export. */
private val NON_EXPORT_NAMES = setOf(
    "manifest.json", "config.json", "package.json", "package-lock.json", "tsconfig.json",
    "composer.json", "renovate.json", ".eslintrc.json",
)

/**
 * Whether a `.json` path in the repo can be a graph export worth offering in the File dropdown and the graph
 * switcher. Excludes the backup repo's own `manifest.json` and `config.example.json`, plus common tooling
 * files (`*.example.json`, `*.sample.json`, `package.json`, …).
 */
fun isGraphExportCandidate(path: String): Boolean {
    val name = path.substringAfterLast('/').lowercase()
    if (!name.endsWith(".json")) return false
    if (name in NON_EXPORT_NAMES) return false
    if (name.endsWith(".example.json") || name.endsWith(".sample.json") || name.endsWith(".template.json")) return false
    if (name.startsWith("config.")) return false
    return true
}
