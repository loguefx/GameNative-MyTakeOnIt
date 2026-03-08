package app.gamenative.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.data.FriendMessage
import app.gamenative.data.OwnedGames
import app.gamenative.db.dao.ChatMessageDao
import app.gamenative.service.SteamService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Holds friend's owned games and chat message history for the profile.
 */
@HiltViewModel
class FriendDetailViewModel @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
) : ViewModel() {

    private val _ownedGames = MutableStateFlow<List<OwnedGames>>(emptyList())
    val ownedGames: StateFlow<List<OwnedGames>> = _ownedGames.asStateFlow()

    fun loadOwnedGames(friendSteamId: Long) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    SteamService.getOwnedGames(friendSteamId)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _ownedGames.value = list
                .sortedWith(
                    compareByDescending<OwnedGames> { it.rtimeLastPlayed }
                        .thenByDescending { it.playtimeForever }
                )
        }
    }

    fun messages(steamIdFriend: Long): Flow<List<FriendMessage>> =
        chatMessageDao.getByFriend(steamIdFriend)

    fun sendMessage(steamIdFriend: Long, text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatMessageDao.insert(
                    FriendMessage(
                        steamIDFriend = steamIdFriend,
                        fromLocal = true,
                        message = text,
                        timestamp = (System.currentTimeMillis() / 1000).toInt(),
                    ),
                )
            }
            SteamService.sendFriendMessage(steamIdFriend, text)
        }
    }
}
