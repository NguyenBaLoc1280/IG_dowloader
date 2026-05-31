package com.example

import android.app.Dialog
import android.util.Log
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.DownloadRecord
import com.example.data.IgHighlight
import com.example.data.IgMedia
import com.example.data.IgProfile
import com.example.data.IgStory
import com.example.ui.InstagramViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.UiState
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.AppLanguage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    InstagramDownloaderApp(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

// Set up sleek, tech-oriented premium true-black color palette (resembling X / xAI)
val SpaceDarkBg = Color(0xFF000000)
val CardSlateBg = Color(0xFF16181C)
val NeonAccent = Color(0xFFFFFFFF)
val RadiantSecondary = Color(0xFF000000)
val TextSilver = Color(0xFF71767B)

@Composable
fun InstagramDownloaderApp(
    modifier: Modifier = Modifier,
    viewModel: InstagramViewModel = viewModel()
) {
    val context = LocalContext.current

    // Observe State flows
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val loggedInUsername by viewModel.loggedInUsername.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val postsList by viewModel.postsList.collectAsStateWithLifecycle()
    val storiesList by viewModel.storiesList.collectAsStateWithLifecycle()
    val storiesLoading by viewModel.storiesLoading.collectAsStateWithLifecycle()
    val storiesError by viewModel.storiesError.collectAsStateWithLifecycle()
    val highlightsList by viewModel.highlightsList.collectAsStateWithLifecycle()
    val highlightsLoading by viewModel.highlightsLoading.collectAsStateWithLifecycle()
    val highlightsError by viewModel.highlightsError.collectAsStateWithLifecycle()
    val selectedHighlightId by viewModel.selectedHighlightId.collectAsStateWithLifecycle()
    val highlightItemsList by viewModel.highlightItemsList.collectAsStateWithLifecycle()
    val highlightItemsLoading by viewModel.highlightItemsLoading.collectAsStateWithLifecycle()
    val highlightItemsError by viewModel.highlightItemsError.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val statusMessage by viewModel.downloadStatusMessage.collectAsStateWithLifecycle()
    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val postsHasNext by viewModel.postsHasNext.collectAsStateWithLifecycle()
    val isMorePostsLoading by viewModel.isMorePostsLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0: Posts, 1: Stories, 2: Highlights, 3: Offline Downloads
    var showWebViewLogin by remember { mutableStateOf(false) }
    var lightboxMedia by remember { mutableStateOf<LightboxItem?>(null) }
    
    // Collapsible header toggle
    var isHeaderExpanded by remember { mutableStateOf(true) }

    // Display Status Message Toasts
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            val localizedText = if (it.startsWith("Successfully logged in as")) {
                val username = it.substringAfter("as ").removeSuffix("!")
                Translator.get("toast_session", appLanguage).format(username)
            } else if (it.startsWith("Logged out from Instagram")) {
                Translator.get("toast_logged_out", appLanguage)
            } else {
                it
            }
            Toast.makeText(context, localizedText, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    val layoutDirection = if (appLanguage == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Box(
            modifier = modifier
                .background(SpaceDarkBg)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Language Selection and Minimize Header Control Panel Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern compact language capsules
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppLanguage.values().forEach { lang ->
                            val isSelected = appLanguage == lang
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF2F3336) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.White else Color(0xFF202225),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setLanguage(lang) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = lang.displayName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF71767B)
                                )
                            }
                        }
                    }

                    // HQ Quality Boost Toggle + Minimize Control Action Group
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hqEnabled by viewModel.isHqEnhanceEnabled.collectAsStateWithLifecycle()
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (hqEnabled) Color(0xFF0EA5E9).copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (hqEnabled) Color(0xFF38BDF8) else Color(0xFF202225),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setHqEnhanceEnabled(!hqEnabled) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = "HQ Enhancer",
                                    tint = if (hqEnabled) Color(0xFF38BDF8) else Color(255, 255, 255, 120),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = if (appLanguage == AppLanguage.VI) "Bộ tăng cường HQ" else "HQ Boost",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hqEnabled) Color(0xFFF1F5F9) else Color(0xFF71767B)
                                )
                            }
                        }

                        IconButton(
                            onClick = { isHeaderExpanded = !isHeaderExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isHeaderExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isHeaderExpanded) "Minimize Profile" else "Expand Profile",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Header components block
                if (isHeaderExpanded) {
                    Column {
                        // Premium Header with Session Details
                        SessionHeaderRow(
                            isLoggedIn = isLoggedIn,
                            username = loggedInUsername,
                            onLoginClick = { showWebViewLogin = true },
                            onLogoutClick = { viewModel.logout() },
                            appLanguage = appLanguage
                        )

                        // Search Target User Card
                        SearchCard(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearchClick = {
                                viewModel.searchProfile(searchQuery)
                                activeTab = 0 // Switch to posts on search
                            },
                            isLoading = profileState is UiState.Loading,
                            appLanguage = appLanguage
                        )

                        // Render Search Result details if present
                        when (profileState) {
                            is UiState.Success -> {
                                val profile = (profileState as UiState.Success<IgProfile>).data
                                val isFollowing by viewModel.isCurrentProfileFollowed.collectAsStateWithLifecycle()
                                ProfileDetailCard(
                                    profile = profile,
                                    isFollowing = isFollowing,
                                    onFollowToggle = { viewModel.toggleFollowProfile(profile) },
                                    appLanguage = appLanguage
                                )
                            }
                            is UiState.Error -> {
                                ErrorCard(
                                    message = (profileState as UiState.Error).message,
                                    onRetry = { viewModel.searchProfile(searchQuery) }
                                )
                            }
                            else -> {
                                // Friendly instruction placeholder when idle
                                if (downloadHistory.isEmpty()) {
                                    WelcomeBanner(appLanguage = appLanguage)
                                }
                            }
                        }
                    }
                } else {
                    // Ultra-compact header row when minimized
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(Color.Black)
                            .border(1.dp, Color(0xFF2F3336), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            val profile = (profileState as? UiState.Success<IgProfile>)?.data
                            if (profile != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(profile.profilePicUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "@${profile.username}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = Translator.get("app_title", appLanguage),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { isHeaderExpanded = true }
                                .background(Color(0xFF222428), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = Translator.get("expand_btn", appLanguage),
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Navigation tabs: Posts, Stories, Highlights, Library (X-style text only)
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = Color.White,
                            height = 2.dp
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Text(
                                text = Translator.get("tab_posts", appLanguage),
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 0) Color.White else Color(0xFF71767B)
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Text(
                                text = Translator.get("tab_stories", appLanguage),
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 1) Color.White else Color(0xFF71767B)
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = {
                            Text(
                                text = Translator.get("tab_highlights", appLanguage),
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 2) Color.White else Color(0xFF71767B)
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        text = {
                            Text(
                                text = Translator.get("tab_library", appLanguage),
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == 3) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 3) Color.White else Color(0xFF71767B)
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 4,
                        onClick = { activeTab = 4 },
                        text = {
                            Text(
                                text = Translator.get("tab_tracking", appLanguage),
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == 4) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 4) Color.White else Color(0xFF71767B)
                            )
                        }
                    )
                }

                // Tab-specific grids
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    when (activeTab) {
                        0 -> {
                            PostsGrid(
                                posts = postsList,
                                selectedItems = selectedItems,
                                onToggleSelect = { viewModel.toggleSelection(it) },
                                onItemClick = { post ->
                                    lightboxMedia = LightboxItem(
                                        mediaId = post.id,
                                        url = post.downloadUrl,
                                        isVideo = post.isVideo,
                                        isStory = false
                                    )
                                },
                                onSelectAll = { viewModel.selectAll(postsList.map { it.id }) },
                                onClearSelection = { viewModel.clearSelection() },
                                appLanguage = appLanguage,
                                hasNextPage = postsHasNext,
                                isMoreLoading = isMorePostsLoading,
                                onLoadMore = { viewModel.loadMorePosts() }
                            )
                        }
                        1 -> {
                            StoriesGrid(
                                stories = storiesList,
                                isLoading = storiesLoading,
                                error = storiesError,
                                selectedItems = selectedItems,
                                onToggleSelect = { viewModel.toggleSelection(it) },
                                onItemClick = { story ->
                                    lightboxMedia = LightboxItem(
                                        mediaId = story.id,
                                        url = story.downloadUrl,
                                        isVideo = story.isVideo,
                                        isStory = true
                                    )
                                },
                                isLoggedIn = isLoggedIn,
                                onTriggerLogin = { showWebViewLogin = true },
                                onSelectAll = { viewModel.selectAll(storiesList.map { it.id }) },
                                onClearSelection = { viewModel.clearSelection() },
                                appLanguage = appLanguage
                            )
                        }
                        2 -> {
                            HighlightsTabContent(
                                highlights = highlightsList,
                                isLoadingHighlights = highlightsLoading,
                                errorHighlights = highlightsError,
                                selectedHighlightId = selectedHighlightId,
                                highlightItems = highlightItemsList,
                                isLoadingHighlightItems = highlightItemsLoading,
                                errorHighlightItems = highlightItemsError,
                                selectedItems = selectedItems,
                                onHighlightClick = { highlight ->
                                    if (selectedHighlightId == highlight.id) {
                                        viewModel.selectHighlight(null)
                                    } else {
                                        viewModel.selectHighlight(highlight.id)
                                    }
                                },
                                onToggleSelect = { viewModel.toggleSelection(it) },
                                onItemClick = { item ->
                                    lightboxMedia = LightboxItem(
                                        mediaId = item.id,
                                        url = item.downloadUrl,
                                        isVideo = item.isVideo,
                                        isStory = true
                                    )
                                },
                                isLoggedIn = isLoggedIn,
                                onTriggerLogin = { showWebViewLogin = true },
                                onSelectAll = { viewModel.selectAll(highlightItemsList.map { it.id }) },
                                onClearSelection = { viewModel.clearSelection() },
                                appLanguage = appLanguage
                            )
                        }
                        3 -> {
                            DownloadsLibrary(
                                records = downloadHistory,
                                onDelete = { record -> viewModel.deleteDownload(record.id) },
                                appLanguage = appLanguage
                            )
                        }
                        4 -> {
                            TrackingTabContent(
                                viewModel = viewModel,
                                onSelectAccount = { username ->
                                    viewModel.searchProfile(username)
                                    activeTab = 0
                                },
                                appLanguage = appLanguage
                            )
                        }
                    }
                }
            }

            // Action Floating Bar for Multi-select downloading (X themed black bar)
            if (selectedItems.isNotEmpty() && activeTab != 3 && activeTab != 4) {
                val targetUser = (profileState as? UiState.Success)?.data?.username ?: searchQuery
                MultiSelectActionBar(
                    selectedCount = selectedItems.size,
                    onDownloadClick = {
                        viewModel.downloadSelected(targetUser)
                    },
                    onCancelClick = {
                        viewModel.clearSelection()
                    },
                    isDownloading = isDownloading,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    appLanguage = appLanguage
                )
            }
        }

        // Embed Instagram Authorization WebView
        if (showWebViewLogin) {
            InstagramLoginDialog(
                onSuccess = { cookies, name ->
                    viewModel.saveSession(cookies, name)
                    showWebViewLogin = false
                },
                onDismiss = { showWebViewLogin = false }
            )
        }

        // Media Preview Lightbox Modal
        lightboxMedia?.let { item ->
            MediaLightbox(
                item = item,
                onDismiss = { lightboxMedia = null },
                onDirectDownload = {
                    val targetUser = (profileState as? UiState.Success)?.data?.username ?: "download"
                    viewModel.downloadSelected(targetUser)
                    lightboxMedia = null
                }
            )
        }
    }
}

// Dynamic container model for lightboxes
data class LightboxItem(
    val mediaId: String,
    val url: String,
    val isVideo: Boolean,
    val isStory: Boolean
)

@Composable
fun SessionHeaderRow(
    isLoggedIn: Boolean,
    username: String?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    appLanguage: AppLanguage
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2F3336))
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = Color.Black,
                            shape = CircleShape
                        )
                        .border(1.dp, Color(0xFF2F3336), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = Translator.get("app_title", appLanguage),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isLoggedIn) Translator.get("session_active", appLanguage).format(username) else Translator.get("session_guest", appLanguage),
                        fontSize = 10.sp,
                        color = if (isLoggedIn) Color.White else TextSilver,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isLoggedIn) {
                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier.size(32.dp).testTag("logout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Logout",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Button(
                    onClick = onLoginClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("login_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = Translator.get("sign_in", appLanguage),
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean,
    appLanguage: AppLanguage
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF2F3336))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(Translator.get("search_placeholder", appLanguage), color = TextSilver, fontSize = 13.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("username_input"),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSilver, modifier = Modifier.size(18.dp))
                }
            )

            Button(
                onClick = onSearchClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading && query.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("search_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "GO",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileDetailCard(
    profile: IgProfile,
    isFollowing: Boolean,
    onFollowToggle: () -> Unit,
    appLanguage: AppLanguage
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2F3336))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profile.profilePicUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "User Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.fullName.ifEmpty { "@${profile.username}" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "@${profile.username}",
                        fontSize = 11.sp,
                        color = TextSilver
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (profile.isPrivate) Color(0x1AEF4444) else Color(0x1A10B981),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (profile.isPrivate) Icons.Default.Lock else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (profile.isPrivate) Color(0xFFEF4444) else Color(0xFF10B981),
                                    modifier = Modifier.size(8.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (profile.isPrivate) Translator.get("badge_private", appLanguage) else Translator.get("badge_public", appLanguage),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (profile.isPrivate) Color(0xFFEF4444) else Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onFollowToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) Color(0xFF2F3336) else Color.White,
                        contentColor = if (isFollowing) Color.White else Color.Black
                    ),
                    border = if (isFollowing) BorderStroke(1.dp, Color(0xFF536471)) else null,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 1.dp)
                        .testTag("follow_toggle_btn")
                ) {
                    val label = if (isFollowing) {
                        Translator.get("btn_unfollow", appLanguage)
                    } else {
                        Translator.get("btn_follow", appLanguage)
                    }
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (profile.biography.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = profile.biography,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricColumn(label = Translator.get("posts_lbl", appLanguage), value = formatCounter(profile.mediaCount))
                MetricColumn(label = Translator.get("followers_lbl", appLanguage), value = formatCounter(profile.followerCount))
                MetricColumn(label = Translator.get("following_lbl", appLanguage), value = formatCounter(profile.followingCount))
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextSilver
        )
    }
}

private fun formatCounter(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000f)
        else -> count.toString()
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1FDD2E2E)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFEF4444))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Info, contentDescription = "Error", tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Retry Fetch", color = NeonAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun WelcomeBanner(appLanguage: AppLanguage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color.Black, CircleShape)
                .border(2.dp, Color(0xFF2F3336), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = Translator.get("welcome_title", appLanguage),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = Translator.get("welcome_subtitle", appLanguage),
            fontSize = 12.sp,
            color = TextSilver,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostsGrid(
    posts: List<IgMedia>,
    selectedItems: Set<String>,
    onToggleSelect: (String) -> Unit,
    onItemClick: (IgMedia) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    appLanguage: AppLanguage,
    hasNextPage: Boolean,
    isMoreLoading: Boolean,
    onLoadMore: () -> Unit
) {
    if (posts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(Translator.get("no_posts", appLanguage), color = TextSilver, fontSize = 13.sp)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CardSelectionHeader(
            itemsCount = posts.size,
            selectedCount = selectedItems.size,
            onSelectAll = onSelectAll,
            onClear = onClearSelection,
            appLanguage = appLanguage
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(posts) { post ->
                val isSelected = selectedItems.contains(post.id)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardSlateBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color(0xFF222428),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .combinedClickable(
                            onClick = { onItemClick(post) },
                            onLongClick = { onToggleSelect(post.id) }
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(post.displayUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Post Item",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Badges for video/sidecar format
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        if (post.isVideo) {
                            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        } else if (post.type == "Sidecar") {
                            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                                Icon(Icons.Default.LibraryBooks, contentDescription = "Carousel", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Selection Check overlay
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(20.dp)
                        )
                    }
                }
            }

            if (hasNextPage) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        LaunchedEffect(Unit) {
                            onLoadMore()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoriesGrid(
    stories: List<IgStory>,
    isLoading: Boolean,
    error: String?,
    selectedItems: Set<String>,
    onToggleSelect: (String) -> Unit,
    onItemClick: (IgStory) -> Unit,
    isLoggedIn: Boolean,
    onTriggerLogin: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    appLanguage: AppLanguage
) {

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error, color = Color(0xFFEF4444), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        return
    }

    if (stories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Translator.get("no_stories", appLanguage), color = TextSilver, fontSize = 13.sp)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isLoggedIn) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF0EA5E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Guest Mode",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.VI) "Chế độ Khách (Chưa đăng nhập)" else "Guest Mode (Not Signed In)",
                            color = Color(0xFFF1F5F9),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = Translator.get("login_details", appLanguage),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Button(
                        onClick = onTriggerLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.align(Alignment.End).height(32.dp)
                    ) {
                        Text(
                            text = Translator.get("sign_in", appLanguage),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        CardSelectionHeader(
            itemsCount = stories.size,
            selectedCount = selectedItems.size,
            onSelectAll = onSelectAll,
            onClear = onClearSelection,
            appLanguage = appLanguage
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(stories) { story ->
                val isSelected = selectedItems.contains(story.id)
                Box(
                    modifier = Modifier
                        .aspectRatio(0.6f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardSlateBg)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color(0xFF222428),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onToggleSelect(story.id) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(story.displayUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Story Slide",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (story.isVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Video story", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Preview",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(16.dp)
                            .clickable { onItemClick(story) }
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightsTabContent(
    highlights: List<IgHighlight>,
    isLoadingHighlights: Boolean,
    errorHighlights: String?,
    selectedHighlightId: String?,
    highlightItems: List<IgStory>,
    isLoadingHighlightItems: Boolean,
    errorHighlightItems: String?,
    selectedItems: Set<String>,
    onHighlightClick: (IgHighlight) -> Unit,
    onToggleSelect: (String) -> Unit,
    onItemClick: (IgStory) -> Unit,
    isLoggedIn: Boolean,
    onTriggerLogin: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    appLanguage: AppLanguage
) {

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = Translator.get("highlights_tray", appLanguage),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )

        if (!isLoggedIn) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF0EA5E9)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Guest Mode",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.VI) "Chế độ Khách (Chưa đăng nhập)" else "Guest Mode (Not Signed In)",
                            color = Color(0xFFF1F5F9),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = Translator.get("login_details_highlights", appLanguage),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Button(
                        onClick = onTriggerLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.align(Alignment.End).height(32.dp)
                    ) {
                        Text(
                            text = Translator.get("sign_in", appLanguage),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (isLoadingHighlights) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            }
        } else if (errorHighlights != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(errorHighlights, color = Color(0xFFEF4444), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        } else if (highlights.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(Translator.get("no_highlights", appLanguage), color = TextSilver, fontSize = 13.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(highlights) { highlight ->
                    val isSelected = selectedHighlightId == highlight.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable { onHighlightClick(highlight) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CardSlateBg)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF222428),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (highlight.coverUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(highlight.coverUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = highlight.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = highlight.title,
                            fontSize = 10.sp,
                            color = if (isSelected) Color.White else Color(0xFF71767B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF2F3336), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

        // Highlight Items
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedHighlightId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = Translator.get("select_highlight_folder", appLanguage),
                        color = TextSilver,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (isLoadingHighlightItems) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (errorHighlightItems != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorHighlightItems, color = Color(0xFFEF4444), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            } else if (highlightItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Translator.get("no_items_highlight", appLanguage), color = TextSilver, fontSize = 13.sp)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    CardSelectionHeader(
                        itemsCount = highlightItems.size,
                        selectedCount = selectedItems.size,
                        onSelectAll = onSelectAll,
                        onClear = onClearSelection,
                        appLanguage = appLanguage
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(highlightItems) { item ->
                            val isSelected = selectedItems.contains(item.id)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.6f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardSlateBg)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color(0xFF222428),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggleSelect(item.id) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.displayUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Highlight Item",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Video overlay indicator
                                if (item.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    ) {
                                        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Video story",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // Direct tap item viewer action helper
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Preview",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(16.dp)
                                        .clickable { onItemClick(item) }
                                )

                                // Selections
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardSelectionHeader(
    itemsCount: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    appLanguage: AppLanguage
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Translator.get("total_assets", appLanguage).format(itemsCount),
            color = TextSilver,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Row {
            TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(Translator.get("btn_select_all", appLanguage), color = Color.White, fontSize = 11.sp)
            }
            if (selectedCount > 0) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("${Translator.get("btn_clear", appLanguage)} ($selectedCount)", color = Color(0xFFEF4444), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DownloadsLibrary(
    records: List<DownloadRecord>,
    onDelete: (DownloadRecord) -> Unit,
    appLanguage: AppLanguage
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, tint = TextSilver.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(Translator.get("storage_empty", appLanguage), color = TextSilver, fontSize = 13.sp)
            }
        }
        return
    }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(records) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                border = BorderStroke(1.dp, Color(0xFF2F3336)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (record.mediaType == "video") {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Video Record", tint = Color.White)
                        } else {
                            Icon(Icons.Default.Image, contentDescription = "Image Record", tint = TextSilver)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "@${record.username}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${record.type} Media",
                            fontSize = 11.sp,
                            color = TextSilver
                        )
                        Text(
                            text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.downloadedAt)),
                            fontSize = 9.sp,
                            color = TextSilver.copy(alpha = 0.6f)
                        )
                    }

                    Row {
                        IconButton(onClick = {
                            viewLocalFile(context, record.localUri, record.mediaType == "video")
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play/Open", tint = Color.White)
                        }

                        IconButton(onClick = { onDelete(record) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Log", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

fun viewLocalFile(context: Context, path: String, isVideo: Boolean) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(path), if (isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Opening downloaded content... Please check your device Downloads folder under IGDownloader.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MultiSelectActionBar(
    selectedCount: Int,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    isDownloading: Boolean,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color(0xFF2F3336)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancelClick, enabled = !isDownloading) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${Translator.get("selected_lbl", appLanguage).format(selectedCount)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onDownloadClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White,
                    disabledContentColor = Color.Black
                ),
                shape = RoundedCornerShape(20.dp),
                enabled = !isDownloading
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Translator.get("downloading", appLanguage), color = Color.Black, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Translator.get("download_all", appLanguage), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InstagramLoginDialog(
    onSuccess: (cookies: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
            border = BorderStroke(1.dp, Color(0xFF44474E))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Instagram Secure Sign-In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Divider(color = Color(0xFF44474E))

                // Embedded Web Login
                Box(modifier = Modifier.weight(1f)) {
                    InstagramLoginWebView(onLoginSuccess = onSuccess)
                }

                // Security Note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1C1E))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Your password is safe: login runs directly on Instagram servers. We only retrieve your cookies to authorize story and private profile parsing.",
                        fontSize = 10.sp,
                        color = TextSilver,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun InstagramLoginWebView(
    onLoginSuccess: (cookies: String, username: String) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Mobile user-agent to load the clean login UI
                    userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
                }

                // Clean cookies before launch to reset login session
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url == null) return

                        val cookies = cookieManager.getCookie("https://www.instagram.com") ?: ""
                        if (cookies.contains("sessionid")) {
                            // Captured successful authentication cookie, parse ds_user_id to form label
                            val userId = cookies.substringAfter("ds_user_id=").substringBefore(";")
                            val friendlyName = "IG_User_$userId"
                            onLoginSuccess(cookies, friendlyName)
                        }
                    }
                }

                loadUrl("https://www.instagram.com/accounts/login/")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MediaLightbox(
    item: LightboxItem,
    onDismiss: () -> Unit,
    onDirectDownload: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SpaceDarkBg),
            border = BorderStroke(1.dp, Color(0xFF44474E))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Header actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isVideo) "Video Preview" else "Image Preview",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Render Content Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isVideo) {
                        VideoPreviewPlayer(videoUrl = item.url)
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.url)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Preview Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF44474E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back", color = Color.White)
                    }

                    Button(
                        onClick = onDirectDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = RadiantSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download Media", color = RadiantSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPreviewPlayer(videoUrl: String) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(key1 = videoUrl) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (e: Exception) {
                Log.e("VideoPreviewPlayer", "Error disposing video view", e)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                videoViewRef = this
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (videoUrl.isNotBlank()) {
                    setVideoPath(videoUrl)
                }
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    try {
                        start()
                    } catch (e: Exception) {
                        Log.e("VideoPreviewPlayer", "Error starting playback", e)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("VideoPreviewPlayer", "Error during playback: what=$what, extra=$extra")
                    true // Handled internally, prevents system crash popup
                }
            }
        },
        update = { view ->
            if (videoUrl.isNotBlank()) {
                try {
                    view.setVideoPath(videoUrl)
                    view.start()
                } catch (e: Exception) {
                    Log.e("VideoPreviewPlayer", "Error updating video path", e)
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun TrackingTabContent(
    viewModel: InstagramViewModel,
    onSelectAccount: (String) -> Unit,
    appLanguage: AppLanguage
) {
    val followed by viewModel.followedAccounts.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = if (appLanguage == AppLanguage.VI) "Tài khoản đã đánh dấu (${followed.size})" else "Bookmarks (${followed.size})",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (followed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color(0xFF71767B).copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (appLanguage == AppLanguage.VI) 
                            "Chưa đánh dấu tài khoản nào.\nNhấn nút \"Lưu\" trên trang cá nhân để lưu thông tin tìm kiếm nhanh!" 
                            else "No saved accounts yet.\nClick \"Save\" on a profile to bookmark them here for quick access!",
                        fontSize = 12.sp,
                        color = Color(0xFF71767B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(followed) { account ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAccount(account.username) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16181C)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2F3336))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Close/Delete button on top right
                            IconButton(
                                onClick = {
                                    viewModel.toggleFollowProfile(
                                        IgProfile(
                                            id = "",
                                            username = account.username,
                                            fullName = account.fullName,
                                            isPrivate = false,
                                            profilePicUrl = account.profilePicUrl
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Bookmark",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.size(56.dp)) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(account.profilePicUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .border(1.5.dp, Color(0xFF38BDF8), CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "@${account.username}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = account.fullName.ifEmpty { "@${account.username}" },
                                    fontSize = 11.sp,
                                    color = TextSilver,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (appLanguage == AppLanguage.VI) "Tìm nhanh" else "Quick Find",
                                        fontSize = 9.sp,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
