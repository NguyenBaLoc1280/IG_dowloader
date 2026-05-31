package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class InstagramEngine(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Match the WebView user-agent exactly to prevent security session invalidation or blocks on private profile requests.
    private val defaultUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
    private val appId = "936619743392459"

    /**
     * Helper to retrieve the highest resolution display resource for post images.
     */
    private fun getBestDisplayUrl(node: JSONObject): String {
        val displayResources = node.optJSONArray("display_resources")
        if (displayResources != null && displayResources.length() > 0) {
            var bestUrl = node.optString("display_url")
            var maxWidth = 0
            for (i in 0 until displayResources.length()) {
                val res = displayResources.optJSONObject(i) ?: continue
                val width = res.optInt("config_width", 0)
                if (width > maxWidth) {
                    maxWidth = width
                    bestUrl = res.optString("src")
                }
            }
            if (!bestUrl.isNullOrEmpty()) {
                return bestUrl
            }
        }
        return node.optString("display_url")
    }

    /**
     * Fetch Instagram user profile and recent feed posts
     */
    suspend fun fetchProfileAndPosts(username: String, cookies: String?): Result<IgProfileAndPosts> = withContext(Dispatchers.IO) {
        try {
            val trimmedUsername = username.trim().lowercase()
            val url = "https://www.instagram.com/api/v1/users/web_profile_info/?username=$trimmedUsername"
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("X-IG-App-ID", appId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Referer", "https://www.instagram.com/$trimmedUsername/")
                .header("X-ASBD-ID", "129477")
                .header("X-IG-WWW-Claim", "0")

            if (!cookies.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookies)
                
                val csrfToken = cookies.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("csrftoken=") }
                    ?.substringAfter("csrftoken=")
                if (!csrfToken.isNullOrEmpty()) {
                    requestBuilder.header("X-CSRFToken", csrfToken)
                }
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use Result.failure<IgProfileAndPosts>(
                    Exception("Empty response from Instagram server.")
                )

                if (response.code == 404) {
                    return@use Result.failure<IgProfileAndPosts>(
                        Exception("User profile '$trimmedUsername' was not found on Instagram.")
                    )
                }

                if (response.code == 403 || response.code == 401) {
                    return@use Result.failure<IgProfileAndPosts>(
                        Exception("Access Denied (403/401). If this is a private account or Instagram is rate-limiting, please ensure you are logged in.")
                    )
                }

                if (!response.isSuccessful) {
                    return@use Result.failure<IgProfileAndPosts>(
                        Exception("Instagram server returned an error: Code ${response.code}")
                    )
                }

                try {
                    val root = JSONObject(body)
                    val data = root.optJSONObject("data") ?: return@use Result.failure<IgProfileAndPosts>(
                        Exception("Invalid Instagram response structure (missing 'data'). Please login and try again.")
                    )
                    
                    val user = data.optJSONObject("user") ?: return@use Result.failure<IgProfileAndPosts>(
                        Exception("User profile data not returned by Instagram.")
                    )

                    val profile = IgProfile(
                        id = user.optString("id"),
                        username = user.optString("username"),
                        fullName = user.optString("full_name"),
                        isPrivate = user.optBoolean("is_private"),
                        profilePicUrl = user.optString("profile_pic_url_hd"),
                        followerCount = user.optJSONObject("edge_followed_by")?.optInt("count") ?: 0,
                        followingCount = user.optJSONObject("edge_follow")?.optInt("count") ?: 0,
                        mediaCount = user.optJSONObject("edge_owner_to_timeline_media")?.optInt("count") ?: 0,
                        biography = user.optString("biography")
                    )

                    val mediaList = mutableListOf<IgMedia>()
                    val timeline = user.optJSONObject("edge_owner_to_timeline_media")
                    var endCursor: String? = null
                    var hasNextPage = false
                    
                    if (timeline != null) {
                        val pageInfo = timeline.optJSONObject("page_info")
                        if (pageInfo != null) {
                            hasNextPage = pageInfo.optBoolean("has_next_page", false)
                            endCursor = pageInfo.optString("end_cursor", "").ifEmpty { null }
                        }
                        
                        val edges = timeline.optJSONArray("edges")
                        if (edges != null) {
                            for (i in 0 until edges.length()) {
                                val edge = edges.optJSONObject(i) ?: continue
                                val node = edge.optJSONObject("node") ?: continue
                                mediaList.add(parseMediaNode(node))
                            }
                        }
                    }

                    // Fallback to mobile feed API for private accounts (or if timeline list is empty, but count > 0)
                    if (mediaList.isEmpty() && profile.mediaCount > 0 && !cookies.isNullOrEmpty()) {
                        fetchUserFeedFromApi(profile.id, cookies).onSuccess { (feedList, nextMaxId) ->
                            if (feedList.isNotEmpty()) {
                                mediaList.addAll(feedList)
                                endCursor = nextMaxId
                                hasNextPage = nextMaxId != null
                            }
                        }.onFailure { err ->
                            Log.e("InstagramEngine", "Feed fallback failed: ${err.message}")
                        }
                    }

                    Result.success(IgProfileAndPosts(profile, mediaList, endCursor, hasNextPage))
                } catch (e: Exception) {
                    Log.e("InstagramEngine", "JSON parsing failed", e)
                    Result.failure(Exception("Failed to decode Instagram profile data. Please verify your connection or try logging in. Error: ${e.localizedMessage}"))
                }
            }
        } catch (e: Exception) {
            Log.e("InstagramEngine", "Network failure in fetchProfileAndPosts", e)
            Result.failure(Exception("Network request failed: ${e.localizedMessage}"))
        }
    }

    /**
     * Fetch more posts (load next pages) using GraphQL query or raw feed API.
     */
    suspend fun fetchMorePosts(
        userId: String,
        username: String,
        endCursor: String,
        cookies: String?
    ): Result<IgMorePostsResponse> = withContext(Dispatchers.IO) {
        try {
            if (!cookies.isNullOrEmpty()) {
                // If logged in, preferred way is mobile feed API with pagination
                fetchUserFeedFromApi(userId, cookies, endCursor).onSuccess { (posts, nextCursor) ->
                    return@withContext Result.success(IgMorePostsResponse(posts, nextCursor, nextCursor != null))
                }
            }
            
            // Public pagination using GraphQL query (doesn't require credentials for public profiles)
            val variables = JSONObject().apply {
                put("id", userId)
                put("first", 12)
                put("after", endCursor)
            }.toString()
            val url = "https://www.instagram.com/graphql/query/?doc_id=8704289389650080&variables=$variables"
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("X-IG-App-ID", appId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Referer", "https://www.instagram.com/$username/")
            
            if (!cookies.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookies)
            }
            
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use Result.failure<IgMorePostsResponse>(
                    Exception("Empty response during pagination.")
                )
                if (!response.isSuccessful) {
                    return@use Result.failure<IgMorePostsResponse>(
                        Exception("Server error during pagination: Code ${response.code}")
                    )
                }
                
                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: return@use Result.failure<IgMorePostsResponse>(
                    Exception("Failed to paginate: missing 'data' block.")
                )
                val user = data.optJSONObject("user") ?: return@use Result.failure<IgMorePostsResponse>(
                    Exception("Failed to paginate: missing 'user' block.")
                )
                val timeline = user.optJSONObject("edge_owner_to_timeline_media") ?: return@use Result.failure<IgMorePostsResponse>(
                    Exception("Failed to paginate: missing timeline media.")
                )
                
                val pageInfo = timeline.optJSONObject("page_info")
                var nextCursor: String? = null
                var hasNextPage = false
                if (pageInfo != null) {
                    hasNextPage = pageInfo.optBoolean("has_next_page", false)
                    nextCursor = pageInfo.optString("end_cursor", "").ifEmpty { null }
                }
                
                val posts = mutableListOf<IgMedia>()
                val edges = timeline.optJSONArray("edges")
                if (edges != null) {
                    for (i in 0 until edges.length()) {
                        val edge = edges.optJSONObject(i) ?: continue
                        val node = edge.optJSONObject("node") ?: continue
                        posts.add(parseMediaNode(node))
                    }
                }
                
                Result.success(IgMorePostsResponse(posts, nextCursor, hasNextPage))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Could not load more posts: ${e.localizedMessage}"))
        }
    }

    /**
     * Fetch user stories using the Instagram Stories JSON Api
     */
    suspend fun fetchStories(userId: String, cookies: String?): Result<List<IgStory>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=$userId"
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("X-IG-App-ID", appId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Referer", "https://www.instagram.com/")
                .header("X-ASBD-ID", "129477")
                .header("X-IG-WWW-Claim", "0")

            if (!cookies.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookies)
                val csrfToken = cookies.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("csrftoken=") }
                    ?.substringAfter("csrftoken=")
                if (!csrfToken.isNullOrEmpty()) {
                    requestBuilder.header("X-CSRFToken", csrfToken)
                }
            } else {
                return@withContext Result.failure<List<IgStory>>(
                    Exception("Stories cannot be loaded anonymously. Please sign in to your Instagram account first.")
                )
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use Result.failure<List<IgStory>>(
                    Exception("Empty response from stories service.")
                )

                if (response.code == 403 || response.code == 401) {
                    return@use Result.failure<List<IgStory>>(
                        Exception("Cookie session unauthorized (403/401). Please try logging in again to refresh your Instagram session.")
                    )
                }

                if (!response.isSuccessful) {
                    return@use Result.failure<List<IgStory>>(
                        Exception("Failed to load stories: Servier Code ${response.code}")
                    )
                }

                try {
                    val root = JSONObject(body)
                    val reels = root.optJSONObject("reels")
                    if (reels == null || !reels.has(userId)) {
                        return@use Result.success(emptyList<IgStory>()) // No stories active
                    }

                    val userReel = reels.getJSONObject(userId)
                    val items = userReel.optJSONArray("items") ?: return@use Result.success(emptyList())
                    
                    val storyList = mutableListOf<IgStory>()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        val mediaType = item.optInt("media_type") // 1: Image, 2: Video
                        val isVideo = mediaType == 2
                        val takenAt = item.optLong("taken_at")

                        val displayUrl = getImageUrlFromVersion2(item.optJSONObject("image_versions2"))
                        val videoUrl = if (isVideo) getVideoUrlFromItem(item) else null

                        if (!displayUrl.isNullOrEmpty()) {
                            storyList.add(
                                IgStory(
                                    id = id,
                                    isVideo = isVideo,
                                    displayUrl = displayUrl,
                                    videoUrl = videoUrl,
                                    takenAt = takenAt
                                )
                            )
                        }
                    }
                    Result.success(storyList)
                } catch (e: Exception) {
                    Log.e("InstagramEngine", "JSON parsing failed for stories", e)
                    Result.failure(Exception("Failed to decode Instagram stories: ${e.localizedMessage}"))
                }
            }
        } catch (e: Exception) {
            Log.e("InstagramEngine", "Network failure in fetchStories", e)
            Result.failure(Exception("Could not connect to Instagram stories service: ${e.localizedMessage}"))
        }
    }

    /**
     * Fetch user highlights tray (reels/folders shown on profile page)
     */
    suspend fun fetchHighlights(userId: String, cookies: String?): Result<List<IgHighlight>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://i.instagram.com/api/v1/highlights/$userId/highlights_tray/"
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("X-IG-App-ID", appId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Referer", "https://www.instagram.com/")
                .header("X-ASBD-ID", "129477")
                .header("X-IG-WWW-Claim", "0")

            if (!cookies.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookies)
                val csrfToken = cookies.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("csrftoken=") }
                    ?.substringAfter("csrftoken=")
                if (!csrfToken.isNullOrEmpty()) {
                    requestBuilder.header("X-CSRFToken", csrfToken)
                }
            } else {
                return@withContext Result.failure<List<IgHighlight>>(
                    Exception("Highlights require login to view. Please sign in.")
                )
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use Result.failure<List<IgHighlight>>(
                    Exception("Empty response from highlights service.")
                )

                if (response.code == 403 || response.code == 401) {
                    return@use Result.failure<List<IgHighlight>>(
                        Exception("Cookie session unauthorized (403/401).")
                    )
                }

                if (!response.isSuccessful) {
                    return@use Result.failure<List<IgHighlight>>(
                        Exception("Failed to load highlights list: Server Code ${response.code}")
                    )
                }

                try {
                    val root = JSONObject(body)
                    val tray = root.optJSONArray("tray") ?: return@use Result.success(emptyList<IgHighlight>())
                    
                    val highlightList = mutableListOf<IgHighlight>()
                    for (i in 0 until tray.length()) {
                        val item = tray.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        val title = item.optString("title") ?: "Highlight"
                        
                        val coverMedia = item.optJSONObject("cover_media")
                        var coverUrl = ""
                        if (coverMedia != null) {
                            val cropped = coverMedia.optJSONObject("cropped_image_version")
                            if (cropped != null) {
                                coverUrl = cropped.optString("url") ?: ""
                            }
                            if (coverUrl.isEmpty()) {
                                coverUrl = getImageUrlFromVersion2(coverMedia.optJSONObject("image_versions2")) ?: ""
                            }
                        }

                        if (id.isNotEmpty()) {
                            highlightList.add(
                                IgHighlight(
                                    id = id,
                                    title = title,
                                    coverUrl = coverUrl
                                )
                            )
                        }
                    }
                    Result.success(highlightList)
                } catch (e: Exception) {
                    Log.e("InstagramEngine", "JSON parsing failed for highlights tray", e)
                    Result.failure(Exception("Failed to decode highlights: ${e.localizedMessage}"))
                }
            }
        } catch (e: Exception) {
            Log.e("InstagramEngine", "Network failure in fetchHighlights", e)
            Result.failure(Exception("Could not connect to highlights service: ${e.localizedMessage}"))
        }
    }

    /**
     * Fetch highlights story items inside a highlight reel
     */
    suspend fun fetchHighlightItems(highlightId: String, cookies: String?): Result<List<IgStory>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=$highlightId"
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("X-IG-App-ID", appId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Referer", "https://www.instagram.com/")
                .header("X-ASBD-ID", "129477")
                .header("X-IG-WWW-Claim", "0")

            if (!cookies.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookies)
                val csrfToken = cookies.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("csrftoken=") }
                    ?.substringAfter("csrftoken=")
                if (!csrfToken.isNullOrEmpty()) {
                    requestBuilder.header("X-CSRFToken", csrfToken)
                }
            } else {
                return@withContext Result.failure<List<IgStory>>(
                    Exception("Highlights require login to view.")
                )
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use Result.failure<List<IgStory>>(
                    Exception("Empty response from highlight service.")
                )

                if (response.code == 403 || response.code == 401) {
                    return@use Result.failure<List<IgStory>>(
                        Exception("Cookie session unauthorized (403/401).")
                    )
                }

                if (!response.isSuccessful) {
                    return@use Result.failure<List<IgStory>>(
                        Exception("Failed to load highlight items: Code ${response.code}")
                    )
                }

                try {
                    val root = JSONObject(body)
                    val reels = root.optJSONObject("reels")
                    if (reels == null || !reels.has(highlightId)) {
                        return@use Result.success(emptyList<IgStory>())
                    }

                    val reel = reels.getJSONObject(highlightId)
                    val items = reel.optJSONArray("items") ?: return@use Result.success(emptyList())
                    
                    val storyList = mutableListOf<IgStory>()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        val mediaType = item.optInt("media_type") // 1: Image, 2: Video
                        val isVideo = mediaType == 2
                        val takenAt = item.optLong("taken_at")

                        val displayUrl = getImageUrlFromVersion2(item.optJSONObject("image_versions2"))
                        val videoUrl = if (isVideo) getVideoUrlFromItem(item) else null

                        if (!displayUrl.isNullOrEmpty()) {
                            storyList.add(
                                IgStory(
                                    id = id,
                                    isVideo = isVideo,
                                    displayUrl = displayUrl,
                                    videoUrl = videoUrl,
                                    takenAt = takenAt
                                )
                            )
                        }
                    }
                    Result.success(storyList)
                } catch (e: Exception) {
                    Log.e("InstagramEngine", "JSON parsing failed for highlight items", e)
                    Result.failure(Exception("Failed to decode highlight items: ${e.localizedMessage}"))
                }
            }
        } catch (e: Exception) {
            Log.e("InstagramEngine", "Network failure in fetchHighlightItems", e)
            Result.failure(Exception("Could not connect to highlight items service: ${e.localizedMessage}"))
        }
    }

    /**
     * Fetch user's feed from i.instagram.com mobile api (perfect for private accounts you follow)
     */
    suspend fun fetchUserFeedFromApi(userId: String, cookies: String?, maxId: String? = null): Result<Pair<List<IgMedia>, String?>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://i.instagram.com/api/v1/feed/user/$userId/"
            val url = if (maxId != null) "$endpoint?max_id=$maxId" else endpoint
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("X-IG-App-ID", appId)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .header("Referer", "https://www.instagram.com/")
                .header("X-ASBD-ID", "129477")
                .header("X-IG-WWW-Claim", "0")

            if (!cookies.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookies)
                val csrfToken = cookies.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("csrftoken=") }
                    ?.substringAfter("csrftoken=")
                if (!csrfToken.isNullOrEmpty()) {
                    requestBuilder.header("X-CSRFToken", csrfToken)
                }
            } else {
                return@withContext Result.failure<Pair<List<IgMedia>, String?>>(
                    Exception("Authentication required for private posts.")
                )
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use Result.failure<Pair<List<IgMedia>, String?>>(
                    Exception("Empty response from feed service.")
                )

                if (response.code == 403 || response.code == 401) {
                    return@use Result.failure<Pair<List<IgMedia>, String?>>(
                        Exception("Cookie session unauthorized (403/401).")
                    )
                }

                if (!response.isSuccessful) {
                    return@use Result.failure<Pair<List<IgMedia>, String?>>(
                        Exception("Failed to load feed: Server Code ${response.code}")
                    )
                }

                try {
                    val root = JSONObject(body)
                    val items = root.optJSONArray("items") ?: return@use Result.success(Pair(emptyList<IgMedia>(), null))
                    val nextMaxId = root.optString("next_max_id", "").ifEmpty { null }
                    
                    val mediaList = mutableListOf<IgMedia>()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val parsed = parseFeedItem(item)
                        if (parsed != null) {
                            mediaList.add(parsed)
                        }
                    }
                    Result.success(Pair(mediaList, nextMaxId))
                } catch (e: Exception) {
                    Log.e("InstagramEngine", "JSON parsing failed for feed", e)
                    Result.failure(Exception("Failed to decode feed items: ${e.localizedMessage}"))
                }
            }
        } catch (e: Exception) {
            Log.e("InstagramEngine", "Network failure in fetchUserFeedFromApi", e)
            Result.failure(Exception("Could not connect to feed service: ${e.localizedMessage}"))
        }
    }

    private fun parseFeedItem(item: JSONObject): IgMedia? {
        val id = item.optString("id")
        val code = item.optString("code")
        val mediaType = item.optInt("media_type", 1) // 1: Image, 2: Video, 8: Carousel/Sidecar
        
        // Parse Caption
        var caption = ""
        val captionObj = item.optJSONObject("caption")
        if (captionObj != null) {
            caption = captionObj.optString("text") ?: ""
        }

        val isVideo = mediaType == 2
        val videoUrl = if (isVideo) getVideoUrlFromItem(item) else null
        val displayUrl = getImageUrlFromVersion2(item.optJSONObject("image_versions2")) ?: ""

        val children = mutableListOf<IgMediaChild>()
        if (mediaType == 8) {
            val carouselMedia = item.optJSONArray("carousel_media")
            if (carouselMedia != null) {
                for (i in 0 until carouselMedia.length()) {
                    val childItem = carouselMedia.optJSONObject(i) ?: continue
                    val cId = childItem.optString("id")
                    val cMediaType = childItem.optInt("media_type", 1)
                    val cIsVideo = cMediaType == 2
                    val cVideoUrl = if (cIsVideo) getVideoUrlFromItem(childItem) else null
                    val cDisplayUrl = getImageUrlFromVersion2(childItem.optJSONObject("image_versions2")) ?: ""
                    children.add(IgMediaChild(cId, cDisplayUrl, cVideoUrl, cIsVideo))
                }
            }
        }

        val typeStr = when (mediaType) {
            2 -> "Video"
            8 -> "Sidecar"
            else -> "Image"
        }

        // If displayUrl is empty and we have a carousel, fallback to the first child's displayUrl
        val resolvedDisplayUrl = if (displayUrl.isEmpty() && children.isNotEmpty()) {
            children[0].displayUrl
        } else {
            displayUrl
        }

        if (id.isEmpty()) return null

        return IgMedia(
            id = id,
            type = typeStr,
            displayUrl = resolvedDisplayUrl,
            videoUrl = videoUrl,
            isVideo = isVideo,
            caption = caption,
            shortcode = code,
            children = children
        )
    }

    private fun parseMediaNode(node: JSONObject): IgMedia {
        val id = node.optString("id")
        val shortcode = node.optString("shortcode")
        val typename = node.optString("__typename")
        val displayUrl = getBestDisplayUrl(node)
        val isVideo = node.optBoolean("is_video")
        val videoUrl = if (isVideo) node.optString("video_url") else null

        // Parse Caption
        var caption = ""
        val captionEdgeList = node.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
        if (captionEdgeList != null && captionEdgeList.length() > 0) {
            caption = captionEdgeList.optJSONObject(0)?.optJSONObject("node")?.optString("text") ?: ""
        }

        // Parse Sidecar Carousel
        val children = mutableListOf<IgMediaChild>()
        if (typename == "GraphSidecar") {
            val sidecarEdges = node.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            if (sidecarEdges != null) {
                for (i in 0 until sidecarEdges.length()) {
                    val childEdge = sidecarEdges.optJSONObject(i) ?: continue
                    val childNode = childEdge.optJSONObject("node") ?: continue
                    val cId = childNode.optString("id")
                    val cDisplayUrl = getBestDisplayUrl(childNode)
                    val cIsVideo = childNode.optBoolean("is_video")
                    val cVideoUrl = if (cIsVideo) childNode.optString("video_url") else null
                    children.add(IgMediaChild(cId, cDisplayUrl, cVideoUrl, cIsVideo))
                }
            }
        }

        val typeStr = when (typename) {
            "GraphVideo" -> "Video"
            "GraphSidecar" -> "Sidecar"
            else -> "Image"
        }

        return IgMedia(
            id = id,
            type = typeStr,
            displayUrl = displayUrl,
            videoUrl = videoUrl,
            isVideo = isVideo,
            caption = caption,
            shortcode = shortcode,
            children = children
        )
    }

    private fun getImageUrlFromVersion2(imgVersion: JSONObject?): String? {
        if (imgVersion == null) return null
        val candidates = imgVersion.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        
        var bestUrl = candidates.optJSONObject(0)?.optString("url")
        var maxWidth = 0
        for (i in 0 until candidates.length()) {
            val cand = candidates.optJSONObject(i) ?: continue
            val width = cand.optInt("width", 0)
            if (width > maxWidth) {
                maxWidth = width
                bestUrl = cand.optString("url")
            }
        }
        return bestUrl
    }

    private fun getVideoUrlFromItem(item: JSONObject): String? {
        val videos = item.optJSONArray("video_versions") ?: return null
        if (videos.length() == 0) return null
        // First entry has the highest resolution
        return videos.optJSONObject(0)?.optString("url")
    }

    private fun enhanceImageQuality(srcBytes: ByteArray): ByteArray {
        return try {
            val options = BitmapFactory.Options().apply {
                inMutable = true
            }
            val src = BitmapFactory.decodeByteArray(srcBytes, 0, srcBytes.size, options) ?: return srcBytes
            
            val width = src.width
            val height = src.height
            if (width < 100 || height < 100) {
                src.recycle()
                return srcBytes
            }
            
            // Standard high-performance 1D pixel array convolution sharpening
            val pixels = IntArray(width * height)
            src.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val output = IntArray(width * height)
            
            // Copy borders
            System.arraycopy(pixels, 0, output, 0, width)
            System.arraycopy(pixels, (height - 1) * width, output, (height - 1) * width, width)
            for (y in 0 until height) {
                output[y * width] = pixels[y * width]
                output[y * width + width - 1] = pixels[y * width + width - 1]
            }
            
            // Apply lightweight smart Unsharp/Sharpen convolution kernel
            // Center weight: 2.2, Adjacent weights: -0.3
            // Sum of weights = 2.2 - 4 * 0.3 = 1.0 (Preserves overall brightness perfectly!)
            for (y in 1 until height - 1) {
                val yOffset = y * width
                val yPrevOffset = (y - 1) * width
                val yNextOffset = (y + 1) * width
                for (x in 1 until width - 1) {
                    val idx = yOffset + x
                    val center = pixels[idx]
                    val left = pixels[idx - 1]
                    val right = pixels[idx + 1]
                    val top = pixels[yPrevOffset + x]
                    val bottom = pixels[yNextOffset + x]
                    
                    val rC = (center shr 16) and 0xFF
                    val gC = (center shr 8) and 0xFF
                    val bC = center and 0xFF
                    
                    val rL = (left shr 16) and 0xFF
                    val gL = (left shr 8) and 0xFF
                    val bL = left and 0xFF
                    
                    val rR = (right shr 16) and 0xFF
                    val gR = (right shr 8) and 0xFF
                    val bR = right and 0xFF
                    
                    val rT = (top shr 16) and 0xFF
                    val gT = (top shr 8) and 0xFF
                    val bT = top and 0xFF
                    
                    val rB = (bottom shr 16) and 0xFF
                    val gB = (bottom shr 8) and 0xFF
                    val bB = bottom and 0xFF
                    
                    var r = (rC * 2.2f - (rL + rR + rT + rB) * 0.3f).toInt()
                    var g = (gC * 2.2f - (gL + gR + gT + gB) * 0.3f).toInt()
                    var b = (bC * 2.2f - (bL + bR + bT + bB) * 0.3f).toInt()
                    
                    r = if (r < 0) 0 else if (r > 255) 255 else r
                    g = if (g < 0) 0 else if (g > 255) 255 else g
                    b = if (b < 0) 0 else if (b > 255) 255 else b
                    
                    output[idx] = (0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
            }
            
            val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            dest.setPixels(output, 0, width, 0, 0, width, height)
            
            val outStream = ByteArrayOutputStream()
            // Compress with 100% maximum quality JPEG format to prevent any compression noise!
            dest.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
            
            src.recycle()
            dest.recycle()
            
            outStream.toByteArray()
        } catch (e: Exception) {
            Log.e("InstagramEngine", "HQ Image Enhancer failed, fallback to original", e)
            srcBytes
        }
    }

    /**
     * Downloads file from url and saves it locally inside external public storage
     * organized by Username and current date partition.
     * Returns local Uri as string.
     */
    suspend fun downloadMedia(
        username: String,
        mediaId: String,
        url: String,
        isVideo: Boolean,
        hqEnhance: Boolean = true,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val extension = if (isVideo) "mp4" else "jpg"
            val filename = "IG_${username}_${mediaId}_$dateStr.$extension"
            
            // Destination relative folder structure in Downloads: IGDownloader/username_date/
            val relativeDir = "Download/IGDownloader/${username.lowercase()}_$dateStr"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure<String>(Exception("Download error code: ${response.code}"))
                }

                val body = response.body ?: return@use Result.failure<String>(Exception("Empty body downloaded."))
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                // Buffer the entire stream while tracking progress
                val outputBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputBuffer.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        onProgress(totalRead.toFloat() / contentLength)
                    }
                }
                outputBuffer.flush()
                val originalBytes = outputBuffer.toByteArray()

                // Apply premium HQ Image sharpness or save original
                val finalBytes = if (!isVideo && hqEnhance) {
                    enhanceImageQuality(originalBytes)
                } else {
                    originalBytes
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Modern Android: use MediaStore ContentResolver for Downloads target
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                        put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    
                    if (uri == null) {
                        return@use Result.failure<String>(Exception("Unable to create media store entry in Downloads."))
                    }

                    try {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(finalBytes)
                        }

                        // Complete registration
                        contentValues.clear()
                        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)

                        Result.success(uri.toString())
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        Result.failure(e)
                    }
                } else {
                    // Legacy Android (SDK 24-28)
                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val outDir = File(publicDir, "IGDownloader/${username.lowercase()}_$dateStr")
                    if (!outDir.exists()) {
                        outDir.mkdirs()
                    }

                    val outFile = File(outDir, filename)
                    try {
                        FileOutputStream(outFile).use { outputStream ->
                            outputStream.write(finalBytes)
                        }
                        Result.success(outFile.absolutePath)
                    } catch (e: Exception) {
                        if (outFile.exists()) outFile.delete()
                        Result.failure(e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InstagramEngine", "Error downloading media file", e)
            Result.failure(e)
        }
    }

    private fun writeWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (totalBytes > 0) {
                onProgress(totalRead.toFloat() / totalBytes)
            }
        }
        output.flush()
    }
}
