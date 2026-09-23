package com.macrotracker.data.github

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class GitHubSnapshot(
    val user: GitHubUser,
    val issues: List<GitHubIssue> = emptyList(),
    val pullRequests: List<GitHubPullRequest> = emptyList(),
    val activity: List<GitHubActivity> = emptyList(),
    val repos: List<GitHubRepo> = emptyList(),
    val notifications: List<GitHubNotification> = emptyList(),
    val issueTotal: Int = 0,
    val pullTotal: Int = 0,
    val reviewRequestedCount: Int = 0,
    val unreadNotificationCount: Int = 0,
    val notificationsNeedReconnect: Boolean = false,
    val rateLimitRemaining: Int? = null,
    val rateLimitLimit: Int? = null,
    /** A year of contributions, from GraphQL; null when the token could not read it. */
    val contributions: GitHubContributions? = null,
)

/**
 * The contribution calendar github.com draws on a profile: one entry per day of the
 * last year, oldest first, with GitHub's own quartile level (0 none … 4 busiest).
 */
@Serializable
data class GitHubContributions(
    val total: Int,
    val days: List<GitHubContributionDay>,
    /** Contributions to private repos this token cannot itemise. */
    val restricted: Int = 0,
) {
    /** Days in a row with at least one contribution, ending today (or yesterday, if today is still empty). */
    val currentStreak: Int
        get() {
            var i = days.lastIndex
            if (i >= 0 && days[i].count == 0) i-- // today is not over yet
            var n = 0
            while (i >= 0 && days[i].count > 0) {
                n++
                i--
            }
            return n
        }

    val longestStreak: Int
        get() {
            var best = 0
            var run = 0
            days.forEach { day ->
                run = if (day.count > 0) run + 1 else 0
                if (run > best) best = run
            }
            return best
        }
}

@Serializable
data class GitHubContributionDay(
    /** `yyyy-MM-dd`. */
    val date: String,
    val count: Int,
    val level: Int,
)

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val htmlUrl: String,
    val bio: String? = null,
    val publicRepos: Int = 0,
    val totalPrivateRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
)

@Serializable
data class GitHubRepo(
    val owner: String,
    val name: String,
    val fullName: String,
    val description: String? = null,
    val htmlUrl: String,
    val language: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
    val openIssuesCount: Int = 0,
    val isPrivate: Boolean = false,
    val pushedAt: String? = null,
    val updatedAt: String? = null,
    val defaultBranch: String? = null,
    val license: String? = null,
    val homepage: String? = null,
    val ownerAvatarUrl: String? = null,
)

@Serializable
data class GitHubLabel(
    val name: String,
    val color: String,
)

@Serializable
data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val repoFullName: String,
    val userLogin: String,
    val userAvatarUrl: String? = null,
    val comments: Int = 0,
    val labels: List<GitHubLabel> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val assignedToMe: Boolean = false,
    val isPullRequest: Boolean = false,
    val draft: Boolean = false,
)

@Serializable
data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val repoFullName: String,
    val userLogin: String,
    val userAvatarUrl: String? = null,
    val draft: Boolean = false,
    val mergedAt: String? = null,
    val labels: List<GitHubLabel> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val reviewRequested: Boolean = false,
    val authoredByMe: Boolean = false,
)

@Serializable
data class GitHubActivity(
    val id: String,
    val type: String,
    val repoFullName: String,
    val createdAt: String,
    val title: String,
    val htmlUrl: String? = null,
    val actorLogin: String,
    val actorAvatarUrl: String? = null,
)

@Serializable
data class GitHubNotification(
    val id: String,
    val unread: Boolean,
    val reason: String,
    val title: String,
    val type: String,
    val repoFullName: String,
    val updatedAt: String,
    val htmlUrl: String?,
)

@Serializable
data class GitHubCommit(
    val sha: String,
    val message: String,
    val htmlUrl: String,
    val authorLogin: String? = null,
    val authorAvatarUrl: String? = null,
    val committedAt: String? = null,
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    val name: String,
    val displayTitle: String,
    val status: String,
    val conclusion: String? = null,
    val htmlUrl: String,
    val headBranch: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class GitHubRelease(
    val tagName: String,
    val name: String? = null,
    val htmlUrl: String,
    val publishedAt: String? = null,
    val prerelease: Boolean = false,
)

@Serializable
data class GitHubRepoFocus(
    val repo: GitHubRepo,
    val issues: List<GitHubIssue> = emptyList(),
    val pullRequests: List<GitHubPullRequest> = emptyList(),
    val activity: List<GitHubActivity> = emptyList(),
    val commits: List<GitHubCommit> = emptyList(),
    val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
    val latestRelease: GitHubRelease? = null,
)

val GitHubIssue.isOpen: Boolean get() = state.equals("open", ignoreCase = true)

val GitHubPullRequest.isOpen: Boolean get() = state.equals("open", ignoreCase = true) && !draft
val GitHubPullRequest.isMerged: Boolean get() = !mergedAt.isNullOrBlank()
val GitHubPullRequest.isDraftOpen: Boolean get() = draft && state.equals("open", ignoreCase = true)

fun GitHubPullRequest.statusKey(): String = when {
    isMerged -> "merged"
    reviewRequested && state.equals("open", ignoreCase = true) -> "review"
    isDraftOpen -> "draft"
    isOpen -> "open"
    else -> "closed"
}

fun GitHubSnapshot.openIssueCount(): Int = issueTotal.takeIf { it > 0 } ?: issues.count { it.isOpen }
fun GitHubSnapshot.openPrCount(): Int = pullTotal.takeIf { it > 0 } ?: pullRequests.count { it.isOpen || it.draft }

fun GitHubRepo.lastTouchedAt(): Instant? {
    val pushed = parseGitHubInstant(pushedAt)
    val updated = parseGitHubInstant(updatedAt)
    return when {
        pushed != null && updated != null -> if (pushed.isAfter(updated)) pushed else updated
        else -> pushed ?: updated
    }
}

fun List<GitHubRepo>.sortedByRecent(): List<GitHubRepo> =
    sortedByDescending { it.lastTouchedAt()?.toEpochMilli() ?: 0L }

fun GitHubSnapshot.repoNamed(fullName: String): GitHubRepo? =
    repos.firstOrNull { it.fullName.equals(fullName, ignoreCase = true) }

fun GitHubWorkflowRun.statusKey(): String = when {
    status.equals("in_progress", true) || status.equals("queued", true) -> "review"
    conclusion.equals("success", true) -> "success"
    conclusion.equals("failure", true) -> "failure"
    conclusion.equals("cancelled", true) || conclusion.equals("skipped", true) -> "cancelled"
    else -> status.lowercase()
}

fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> {
        val v = n / 1_000_000.0
        if (v % 1.0 == 0.0) "${v.toInt()}M" else "%.1fM".format(v)
    }
    n >= 1_000 -> {
        val v = n / 1_000.0
        if (v % 1.0 == 0.0) "${v.toInt()}k" else "%.1fk".format(v)
    }
    else -> n.toString()
}

fun parseGitHubInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
}
