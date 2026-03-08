package app.gamenative.ui.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.data.SteamAchievement
import app.gamenative.data.repository.AchievementRepository
import app.gamenative.service.SteamService
import app.gamenative.ui.screen.PluviaScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val achievementRepository: AchievementRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val appId: Int = savedStateHandle.get<String>(PluviaScreen.Achievements.ARG_APP_ID)
        ?.toIntOrNull() ?: 0

    /** When 0, use current user; otherwise show this Steam ID's achievements (e.g. friend). */
    private val steamIdArg: Long = savedStateHandle.get<String>(PluviaScreen.Achievements.ARG_STEAM_ID)
        ?.toLongOrNull() ?: 0L

    private val steamId: Long
        get() = if (steamIdArg != 0L) steamIdArg else (SteamService.userSteamId?.convertToUInt64() ?: 0L)

    val isViewingFriend: Boolean get() = steamIdArg != 0L

    val achievements: StateFlow<List<SteamAchievement>> =
        achievementRepository.achievements(appId, steamId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /** When viewing a friend's achievements, our achievements for the same game (for compare). */
    val myAchievements: StateFlow<List<SteamAchievement>> =
        achievementRepository.achievements(appId, SteamService.userSteamId?.convertToUInt64() ?: 0L)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch {
            achievementRepository.refreshIfNeeded(appId, steamId)
            if (steamIdArg != 0L) {
                val myId = SteamService.userSteamId?.convertToUInt64() ?: return@launch
                achievementRepository.refreshIfNeeded(appId, myId)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            achievementRepository.refreshIfNeeded(appId, steamId)
        }
    }
}
