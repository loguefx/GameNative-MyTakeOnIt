package app.gamenative.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.db.dao.DownloadingAppInfoDao
import app.gamenative.ui.data.HomeState
import app.gamenative.ui.enums.HomeDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val downloadingAppInfoDao: DownloadingAppInfoDao,
) : ViewModel() {

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _downloadingCount = MutableStateFlow(0)
    val downloadingCount: StateFlow<Int> = _downloadingCount.asStateFlow()

    private val _downloadingAppIds = MutableStateFlow<List<Int>>(emptyList())
    val downloadingAppIds: StateFlow<List<Int>> = _downloadingAppIds.asStateFlow()

    init {
        viewModelScope.launch {
            downloadingAppInfoDao.getAllFlow().collect { list ->
                _downloadingCount.value = list.size
                _downloadingAppIds.value = list.map { it.appId }
            }
        }
    }

    fun onDestination(destination: HomeDestination) {
        _homeState.update { currentState ->
            currentState.copy(currentDestination = destination)
        }
    }

    fun onConfirmExit(value: Boolean) {
        _homeState.update { currentState ->
            currentState.copy(confirmExit = value)
        }
    }
}
