package com.wander.android.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The subset of GitHub's release JSON the update checker actually reads. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false
)
