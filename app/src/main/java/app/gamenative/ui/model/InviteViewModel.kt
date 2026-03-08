package app.gamenative.ui.model

import android.content.Context
import app.gamenative.data.GameInvite
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class InviteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _pendingInvites = MutableStateFlow<List<GameInvite>>(emptyList())
    val pendingInvites: StateFlow<List<GameInvite>> = _pendingInvites.asStateFlow()

    /** When user accepts an invite and no game is running, we store connect for the launch flow. */
    private val _pendingConnectAppId = MutableStateFlow<Int?>(null)
    private val _pendingConnectString = MutableStateFlow<String?>(null)

    fun onInviteReceived(invite: GameInvite) {
        _pendingInvites.update { current -> current + invite }
    }

    fun dismissInvite(inviteId: String) {
        _pendingInvites.update { current -> current.filter { it.id != inviteId } }
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
