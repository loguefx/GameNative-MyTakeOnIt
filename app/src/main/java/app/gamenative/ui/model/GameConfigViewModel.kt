package app.gamenative.ui.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.compat.CompatibilityDb
import app.gamenative.config.ConfigPresets
import app.gamenative.config.GameConfig
import app.gamenative.config.GameConfigRecommender
import app.gamenative.config.GameConfigStore
import app.gamenative.config.ConfigPreset
import app.gamenative.config.KnownGameFixes
import app.gamenative.config.ProtonVersion
import app.gamenative.hardware.HardwareProfile
import app.gamenative.hardware.HardwareProfileCache
import app.gamenative.proton.ProtonTier
import app.gamenative.utils.ContainerUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

@HiltViewModel
class GameConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _config = MutableStateFlow<GameConfig?>(null)
    val config: StateFlow<GameConfig?> = _config.asStateFlow()

    private val _hardware = MutableStateFlow<HardwareProfile?>(null)
    val hardware: StateFlow<HardwareProfile?> = _hardware.asStateFlow()

    private val _protonTier = MutableStateFlow(ProtonTier.UNKNOWN)
    val protonTier: StateFlow<ProtonTier> = _protonTier.asStateFlow()

    /** Set whenever we have a recommender result; used to badge "Recommended" in Proton picker. */
    private val _recommendedProtonVersion = MutableStateFlow<ProtonVersion?>(null)
    val recommendedProtonVersion: StateFlow<ProtonVersion?> = _recommendedProtonVersion.asStateFlow()

    /**
     * The KnownGameFix entry for the current game, or null if the game has no known fix.
     * Drives the fix banner in GameConfigScreen so users see exactly what overrides are active
     * and why — preventing confusion when a game crashes with the "wrong" Proton version.
     */
    private val _knownFix = MutableStateFlow<KnownGameFixes.GameFix?>(null)
    val knownFix: StateFlow<KnownGameFixes.GameFix?> = _knownFix.asStateFlow()

    /**
     * True when the Proton version required by KnownGameFixes is installed on device (either via
     * ContentsManager or bundled in /opt). False means the auto-migration will be silently skipped
     * at launch — the UI must warn the user to download the required version first.
     *
     * Always true when the game has no KnownGameFix protonVersion requirement.
     */
    private val _requiredProtonInstalled = MutableStateFlow(true)
    val requiredProtonInstalled: StateFlow<Boolean> = _requiredProtonInstalled.asStateFlow()

    fun load(appId: String, gameName: String) {
        viewModelScope.launch {
            try {
                val steamAppId = try {
                    ContainerUtils.extractGameIdFromContainerId(appId).toLong()
                } catch (_: Exception) {
                    0L
                }
                if (steamAppId == 0L) {
                    Timber.w("GameConfigViewModel: invalid appId for config $appId")
                    return@launch
                }
                CompatibilityDb.load(context)
                val hw = HardwareProfileCache.getProfile(context)
                _hardware.value = hw
                _protonTier.value = CompatibilityDb.getProtonTierFor(steamAppId)

                // Resolve KnownGameFix and whether the required Proton version is installed.
                // Must run on IO because resolveKnownFixWineVersion calls ContentsManager.syncContents().
                val fix = KnownGameFixes.get(steamAppId.toInt())
                _knownFix.value = fix
                if (fix?.protonVersion != null) {
                    val installed = withContext(Dispatchers.IO) {
                        ContainerUtils.resolveKnownFixWineVersion(context, appId) != null
                    }
                    _requiredProtonInstalled.value = installed
                    if (!installed) {
                        Timber.w("GameConfigViewModel: ${fix.protonVersion.displayName} required for ${fix.gameName} but not installed — launch will use wrong Proton")
                    }
                } else {
                    _requiredProtonInstalled.value = true
                }

                val saved = GameConfigStore.load(context, appId)
                if (saved != null) {
                    _config.value = saved
                    if (hw != null) updateRecommendedProtonVersion(steamAppId.toInt(), gameName, hw)
                    return@launch
                }
                if (hw == null) return@launch
                val recommended = GameConfigRecommender.recommend(
                    appId = steamAppId.toInt(),
                    gameName = gameName,
                    hardware = hw,
                    protonTier = _protonTier.value,
                )
                _recommendedProtonVersion.value = recommended.protonVersion
                GameConfigStore.save(context, appId, recommended)
                _config.value = recommended
            } catch (e: Exception) {
                Timber.e(e, "GameConfigViewModel load failed")
            }
        }
    }

    /**
     * Applies preset: calls ConfigPresets.apply and emits the full result so all UI fields update.
     * Must not only set preset = it; the full config must be replaced.
     */
    fun applyPreset(preset: ConfigPreset) {
        val base = _config.value ?: return
        val hw = _hardware.value ?: return
        _config.value = ConfigPresets.apply(base, preset, hw)
    }

    fun update(newConfig: GameConfig) {
        _config.value = newConfig
    }

    fun resetToRecommended(appId: String, gameName: String) {
        viewModelScope.launch {
            try {
                val steamAppId = try {
                    ContainerUtils.extractGameIdFromContainerId(appId).toLong()
                } catch (_: Exception) {
                    0L
                }
                if (steamAppId == 0L) return@launch
                val hw = _hardware.value ?: HardwareProfileCache.getProfile(context)
                _hardware.value = hw
                if (hw == null) return@launch
                val recommended = GameConfigRecommender.recommend(
                    appId = steamAppId.toInt(),
                    gameName = gameName,
                    hardware = hw,
                    protonTier = _protonTier.value,
                )
                _recommendedProtonVersion.value = recommended.protonVersion
                GameConfigStore.save(context, appId, recommended)
                _config.value = recommended
                // Re-check whether required Proton version is now installed (user may have just downloaded it).
                val fix = KnownGameFixes.get(steamAppId.toInt())
                _knownFix.value = fix
                if (fix?.protonVersion != null) {
                    val installed = withContext(Dispatchers.IO) {
                        ContainerUtils.resolveKnownFixWineVersion(context, appId) != null
                    }
                    _requiredProtonInstalled.value = installed
                }
            } catch (e: Exception) {
                Timber.e(e, "GameConfigViewModel resetToRecommended failed")
            }
        }
    }

    private suspend fun updateRecommendedProtonVersion(appId: Int, gameName: String, hw: HardwareProfile) {
        _recommendedProtonVersion.value = withContext(Dispatchers.Default) {
            GameConfigRecommender.recommend(
                appId = appId,
                gameName = gameName,
                hardware = hw,
                protonTier = _protonTier.value,
            ).protonVersion
        }
    }
}
