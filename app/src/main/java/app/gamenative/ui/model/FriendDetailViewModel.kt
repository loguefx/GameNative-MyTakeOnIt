package app.gamenative.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.Constants
import app.gamenative.data.FriendGame
import app.gamenative.data.FriendMessage
import app.gamenative.data.OwnedGames
import app.gamenative.db.dao.ChatMessageDao
import app.gamenative.service.SteamService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/** Sort option for profile games list. */
const val SORT_MOST_PLAYED = "Most Played"
const val SORT_RECENT = "Recent"
const val SORT_A_Z = "A–Z"

/**
 * Holds friend's owned games (as FriendGame with header URL), sort selection, and chat for the profile.
 */
@HiltViewModel
class FriendDetailViewModel @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
) : ViewModel() {

    private val _ownedGames = MutableStateFlow<List<OwnedGames>>(emptyList())
    val ownedGames: StateFlow<List<OwnedGames>> = _ownedGames.asStateFlow()

    private val _isLoadingGames = MutableStateFlow(true)
    val isLoadingGames: StateFlow<Boolean> = _isLoadingGames.asStateFlow()

    private val _selectedSort = MutableStateFlow(SORT_MOST_PLAYED)
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    val sortedGames: StateFlow<List<FriendGame>> = combine(
        _ownedGames,
        _selectedSort,
    ) { games, sort ->
        val friendGames = games.map { g ->
            FriendGame(
                appId = g.appId,
                name = g.name.ifBlank { "App ${g.appId}" },
                headerUrl = Constants.Library.steamHeaderUrl(g.appId),
                playtimeForever = g.playtimeForever,
                playtime2Weeks = g.playtimeTwoWeeks,
                lastPlayed = if (g.rtimeLastPlayed > 0) g.rtimeLastPlayed.toLong() else null,
            )
        }
        when (sort) {
            SORT_RECENT -> friendGames.sortedByDescending { it.lastPlayed ?: 0L }
            SORT_A_Z -> friendGames.sortedBy { it.name.lowercase() }
            else -> friendGames.sortedByDescending { it.playtimeForever }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(sort: String) {
        _selectedSort.value = sort
    }

    fun loadOwnedGames(friendSteamId: Long) {
        viewModelScope.launch {
            _isLoadingGames.value = true
            val list = withContext(Dispatchers.IO) {
                try {
                    SteamService.getOwnedGames(friendSteamId)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _ownedGames.value = list
            _isLoadingGames.value = false
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
