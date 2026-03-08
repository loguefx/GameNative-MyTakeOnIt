package app.gamenative.ui.screen.xserver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.data.FriendMessage
import app.gamenative.ui.theme.gnAccentGlow
import app.gamenative.ui.theme.gnAccentPrimary
import app.gamenative.ui.theme.gnBgDeepest
import app.gamenative.ui.theme.gnBgElevated
import app.gamenative.ui.theme.gnBgSurface
import app.gamenative.ui.theme.gnBorderCard
import app.gamenative.ui.theme.gnDivider
import app.gamenative.ui.theme.gnTextPrimary
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.ui.theme.gnTextTertiary
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.model.InGameChatViewModel
import app.gamenative.utils.getAvatarURL
import com.skydoves.landscapist.coil.CoilImage
import com.skydoves.landscapist.ImageOptions
import kotlinx.coroutines.delay

private const val PANEL_WIDTH_DP = 320
private const val TOAST_AUTO_DISMISS_MS = 4000L

@Composable
fun InGameChatOverlay(
    viewModel: InGameChatViewModel = hiltViewModel(),
) {
    val panelOpen by viewModel.panelOpen.collectAsStateWithLifecycle()
    val showToast by viewModel.showToast.collectAsStateWithLifecycle()
    val toastSenderName by viewModel.toastSenderName.collectAsStateWithLifecycle()
    val toastMessagePreview by viewModel.toastMessagePreview.collectAsStateWithLifecycle()
    val toastAvatarUrl by viewModel.toastAvatarUrl.collectAsStateWithLifecycle()
    val activeChatSteamId by viewModel.activeChatSteamId.collectAsStateWithLifecycle()

    LaunchedEffect(showToast) {
        if (showToast) {
            delay(TOAST_AUTO_DISMISS_MS)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Edge handle — always visible when panel closed
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(4.dp)
                .height(48.dp)
                .padding(end = if (panelOpen) 0.dp else 4.dp)
                .background(
                    gnAccentPrimary.copy(alpha = if (panelOpen) 0f else 0.5f),
                    RoundedCornerShape(2.dp)
                )
                .clickable { viewModel.togglePanel() }
        )

        // Incoming message toast
        AnimatedVisibility(
            visible = showToast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            IncomingMessageToast(
                senderName = toastSenderName,
                messagePreview = toastMessagePreview,
                avatarUrl = toastAvatarUrl,
                onClick = { viewModel.openPanel(activeChatSteamId ?: 0L) },
            )
        }

        // Side chat panel
        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            val steamId = activeChatSteamId
            if (steamId != null) {
                InGameChatPanelContent(
                    steamId = steamId,
                    viewModel = viewModel,
                    onClose = { viewModel.closePanel() },
                )
            } else {
                // Panel opened via handle with no active chat — show minimal placeholder
                Surface(
                    modifier = Modifier
                        .width(PANEL_WIDTH_DP.dp)
                        .fillMaxHeight(),
                    color = gnBgDeepest.copy(alpha = 0.95f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Open a chat from a message notification",
                            style = PluviaTypography.bodyMedium,
                            color = gnTextSecondary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Close",
                            style = PluviaTypography.labelLarge,
                            color = gnAccentPrimary,
                            modifier = Modifier.clickable { viewModel.closePanel() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingMessageToast(
    senderName: String,
    messagePreview: String,
    avatarUrl: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = PANEL_WIDTH_DP.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = gnBgSurface),
        border = androidx.compose.material3.BorderStroke(1.dp, gnBorderCard),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CoilImage(
                imageModel = { avatarUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = senderName,
                    style = PluviaTypography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = gnTextPrimary,
                )
                Text(
                    text = messagePreview,
                    style = PluviaTypography.bodySmall,
                    color = gnTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Open",
                tint = gnAccentGlow,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun InGameChatPanelContent(
    steamId: Long,
    viewModel: InGameChatViewModel,
    onClose: () -> Unit,
) {
    val friends by app.gamenative.service.SteamService.friendsList.collectAsStateWithLifecycle(initialValue = emptyList())
    val friend = friends.firstOrNull { it.steamId == steamId }
    val messages by viewModel.messages(steamId).collectAsStateWithLifecycle(initialValue = emptyList())
    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .width(PANEL_WIDTH_DP.dp)
            .fillMaxHeight(),
        color = gnBgDeepest.copy(alpha = 0.95f),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gnBgSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CoilImage(
                        imageModel = { friend?.avatarHash?.getAvatarURL() ?: "" },
                        imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                    )
                    Column {
                        Text(
                            text = friend?.name?.takeIf { it.isNotBlank() } ?: "Friend",
                            style = PluviaTypography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = gnTextPrimary,
                        )
                        Text(
                            text = if (friend?.isOnline == true) "Online" else "Offline",
                            style = PluviaTypography.bodySmall,
                            color = gnTextSecondary,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Close chat",
                        tint = gnTextSecondary,
                    )
                }
            }

            Divider(color = gnDivider)

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(messages.reversed(), key = { it.id }) { msg ->
                    InGameChatBubble(message = msg)
                }
            }

            Divider(color = gnDivider)

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gnBgSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Message…", color = gnTextTertiary, style = PluviaTypography.bodyMedium)
                    },
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = gnBgElevated,
                        unfocusedContainerColor = gnBgElevated,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = gnTextPrimary,
                        unfocusedTextColor = gnTextPrimary,
                        cursorColor = gnAccentPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = false,
                    maxLines = 3,
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(steamId, inputText.trim())
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(gnAccentPrimary, RoundedCornerShape(10.dp)),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun InGameChatBubble(message: FriendMessage) {
    val isFromMe = message.fromLocal
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .background(
                    color = if (isFromMe) gnAccentPrimary else gnBgElevated,
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isFromMe) 12.dp else 2.dp,
                        bottomEnd = if (isFromMe) 2.dp else 12.dp,
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = message.message,
                    style = PluviaTypography.bodyMedium,
                    color = if (isFromMe) Color.White else gnTextPrimary,
                )
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = PluviaTypography.labelSmall,
                    color = if (isFromMe) Color.White.copy(alpha = 0.6f) else gnTextTertiary,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

private fun formatMessageTime(unixSeconds: Int): String {
    val now = System.currentTimeMillis() / 1000
    val diff = (now - unixSeconds).coerceAtLeast(0)
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m"
        diff < 86400 -> "${diff / 3600}h"
        else -> "${diff / 86400}d"
    }
}
