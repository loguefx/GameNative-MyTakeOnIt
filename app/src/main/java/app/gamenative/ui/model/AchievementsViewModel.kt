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

    val achievements: StateFlow<List<SteamAchievement>> =
        achievementRepository.achievements(appId, SteamService.userSteamId?.convertToUInt64() ?: 0L)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch {
            val steamId = SteamService.userSteamId?.convertToUInt64() ?: return@launch
            achievementRepository.refreshIfNeeded(appId, steamId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val steamId = SteamService.userSteamId?.convertToUInt64() ?: return@launch
            achievementRepository.refreshIfNeeded(appId, steamId)
        }
    }
}
