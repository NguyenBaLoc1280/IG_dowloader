package com.example.data

data class IgProfile(
    val id: String,
    val username: String,
    val fullName: String,
    val isPrivate: Boolean,
    val profilePicUrl: String,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val mediaCount: Int = 0,
    val biography: String = ""
)

data class IgMedia(
    val id: String,
    val type: String, // "Image", "Video", "Sidecar"
    val displayUrl: String,
    val videoUrl: String? = null,
    val isVideo: Boolean = false,
    val caption: String = "",
    val shortcode: String = "",
    val children: List<IgMediaChild> = emptyList()
) {
    val downloadUrl: String
        get() = if (isVideo && !videoUrl.isNullOrEmpty()) videoUrl else displayUrl
}

data class IgMediaChild(
    val id: String,
    val displayUrl: String,
    val videoUrl: String? = null,
    val isVideo: Boolean = false
) {
    val downloadUrl: String
        get() = if (isVideo && !videoUrl.isNullOrEmpty()) videoUrl else displayUrl
}

data class IgStory(
    val id: String,
    val isVideo: Boolean,
    val displayUrl: String,
    val videoUrl: String? = null,
    val takenAt: Long
) {
    val downloadUrl: String
        get() = if (isVideo && !videoUrl.isNullOrEmpty()) videoUrl else displayUrl
}

data class IgHighlight(
    val id: String,
    val title: String,
    val coverUrl: String,
    val items: List<IgStory> = emptyList()
)

data class IgProfileAndPosts(
    val profile: IgProfile,
    val posts: List<IgMedia>,
    val endCursor: String? = null,
    val hasNextPage: Boolean = false
)

data class IgMorePostsResponse(
    val posts: List<IgMedia>,
    val endCursor: String? = null,
    val hasNextPage: Boolean = false
)
