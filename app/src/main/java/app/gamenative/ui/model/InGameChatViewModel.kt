package app.gamenative.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.data.FriendMessage
import app.gamenative.db.dao.ChatMessageDao
import app.gamenative.service.SteamService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/** Incoming message event for in-game toast (when a friend sends a message while the user is playing). */
data class IncomingChatMessage(
    val senderId: Long,
    val senderName: String,
    val senderAvatarUrl: String,
    val content: String,
)

@HiltViewModel
class InGameChatViewModel @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
) : ViewModel() {

    private val _incomingMessages = MutableSharedFlow<IncomingChatMessage>(extraBufferCapacity = 1)
    val incomingMessages: Flow<IncomingChatMessage> = _incomingMessages

    private val _panelOpen = MutableStateFlow(false)
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    private val _activeChatSteamId = MutableStateFlow<Long?>(null)
    val activeChatSteamId: StateFlow<Long?> = _activeChatSteamId.asStateFlow()

    private val _showToast = MutableStateFlow(false)
    val showToast: StateFlow<Boolean> = _showToast.asStateFlow()

    private val _toastSenderName = MutableStateFlow("")
    val toastSenderName: StateFlow<String> = _toastSenderName.asStateFlow()

    private val _toastMessagePreview = MutableStateFlow("")
    val toastMessagePreview: StateFlow<String> = _toastMessagePreview.asStateFlow()

    private val _toastAvatarUrl = MutableStateFlow("")
    val toastAvatarUrl: StateFlow<String> = _toastAvatarUrl.asStateFlow()

    fun openPanel(steamId: Long) {
        _activeChatSteamId.value = steamId
        _panelOpen.value = true
        _showToast.value = false
    }

    fun closePanel() {
        _panelOpen.value = false
    }

    fun togglePanel() {
        _panelOpen.update { !it }
    }

    fun dismissToast() {
        _showToast.value = false
    }

    /** Call when an incoming chat message is received (e.g. from SteamService when wired). */
    fun onIncomingMessage(msg: IncomingChatMessage) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatMessageDao.insert(
                    FriendMessage(
                        steamIDFriend = msg.senderId,
                        fromLocal = false,
                        message = msg.content,
                        timestamp = (System.currentTimeMillis() / 1000).toInt(),
                    ),
                )
            }
            _toastSenderName.value = msg.senderName
            _toastMessagePreview.value = msg.content.take(60)
            _toastAvatarUrl.value = msg.senderAvatarUrl
            _activeChatSteamId.value = msg.senderId
            _showToast.value = true
            _incomingMessages.emit(msg)
        }
    }

    fun messages(steamId: Long): Flow<List<FriendMessage>> =
        chatMessageDao.getByFriend(steamId)

    fun sendMessage(steamId: Long, text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatMessageDao.insert(
                    FriendMessage(
                        steamIDFriend = steamId,
                        fromLocal = true,
                        message = text,
                        timestamp = (System.currentTimeMillis() / 1000).toInt(),
                    ),
                )
            }
            SteamService.sendFriendMessage(steamId, text)
        }
    }
}
