package app.gamenative.ui.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.data.GameInvite
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _pendingInvites = MutableStateFlow<List<GameInvite>>(emptyList())
    val pendingInvites: StateFlow<List<GameInvite>> = _pendingInvites.asStateFlow()

    /** When user accepts an invite and no game is running, we store connect for the launch flow. */
    private val _pendingConnectAppId = MutableStateFlow<Int?>(null)
    private val _pendingConnectString = MutableStateFlow<String?>(null)

    /** Auto-dismiss jobs keyed by invite ID — cancelled if the user acts before the timer fires. */
    private val dismissJobs = HashMap<String, Job>()

    companion object {
        private const val INVITE_AUTO_DISMISS_MS = 20_000L
    }

    fun onInviteReceived(invite: GameInvite) {
        _pendingInvites.update { current -> current + invite }
        // Cancel any existing timer for this ID (safety for duplicate events) then start a fresh one.
        dismissJobs[invite.id]?.cancel()
        dismissJobs[invite.id] = viewModelScope.launch {
            delay(INVITE_AUTO_DISMISS_MS)
            dismissInvite(invite.id)
        }
    }

    fun dismissInvite(inviteId: String) {
        dismissJobs.remove(inviteId)?.cancel()
        _pendingInvites.update { current -> current.filter { it.id != inviteId } }
    }

    /** Clear all pending invites — call on user logout so stale notifications don't linger. */
    fun clearAll() {
        dismissJobs.values.forEach { it.cancel() }
        dismissJobs.clear()
        _pendingInvites.value = emptyList()
    }

    /**
     * Accept invite: if a game is already running, pass connect string to Wine (TODO);
     * else store connect and navigate to game detail for launch with connect.
     */
    fun acceptInvite(
        invite: GameInvite,
        isGameRunning: Boolean,
        launchedAppId: String?,
        onNavigate: (String) -> Unit,
    ) {
        dismissInvite(invite.id)
        if (isGameRunning && !launchedAppId.isNullOrBlank()) {
            // TODO: pass invite.connectString into the running Wine process
            // e.g. ContainerUtils or WinHandler to run: wine start /unix steam://run/${invite.gameAppId}//${invite.connectString}/
            return
        }
        _pendingConnectAppId.value = invite.gameAppId
        _pendingConnectString.value = invite.connectString
        // Navigate to home; TODO: use game detail route with autoLaunch=true and wire consumePendingConnect() in launch flow
        onNavigate(app.gamenative.ui.screen.PluviaScreen.Home.route)
    }

    /** Consume stored connect string for the launch flow (e.g. when launching after accepting invite). */
    fun consumePendingConnect(): Pair<Int, String>? {
        val appId = _pendingConnectAppId.value ?: return null
        val connect = _pendingConnectString.value ?: return null
        _pendingConnectAppId.value = null
        _pendingConnectString.value = null
        return appId to connect
    }
}
