package app.gamenative.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.data.OwnedGames
import app.gamenative.service.SteamService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Holds friend's owned games for the profile: recently played first, then by total hours (most first).
 */
@HiltViewModel
class FriendDetailViewModel @Inject constructor() : ViewModel() {

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
            // Recently played first (rtimeLastPlayed desc), then by total hours (playtimeForever desc)
            _ownedGames.value = list
                .sortedWith(
                    compareByDescending<OwnedGames> { it.rtimeLastPlayed }
                        .thenByDescending { it.playtimeForever }
                )
        }
    }
}
