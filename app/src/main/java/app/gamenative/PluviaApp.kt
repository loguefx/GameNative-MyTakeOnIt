package app.gamenative

import android.os.StrictMode
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavController
import app.gamenative.debug.DebugSessionLogger
import app.gamenative.events.AndroidEvent
import app.gamenative.events.EventDispatcher
import app.gamenative.service.DownloadService
import app.gamenative.utils.ContainerMigrator
import app.gamenative.utils.IntentLaunchManager
import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.posthog.PersonProfiles
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerView
import com.winlator.xenvironment.XEnvironment
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

// Add PostHog imports
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

// Supabase imports
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.network.supabaseApi
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

typealias NavChangedListener = NavController.OnDestinationChangedListener

@HiltAndroidApp
class PluviaApp : SplitCompatApplication() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Allows to find resource streams not closed within GameNative and JavaSteam
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )

            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        // Init our custom crash handler.
        CrashHandler.initialize(this)

        // Init our datastore preferences.
        PrefManager.init(this)

        // Initialize GOGConstants
        app.gamenative.service.gog.GOGConstants.init(this)

        DownloadService.populateDownloadService(this)

        appScope.launch {
            ContainerMigrator.migrateLegacyContainersIfNeeded(
                context = applicationContext,
                onProgressUpdate = null,
                onComplete = null
            )
        }

        // Clear any stale temporary config overrides from previous app sessions
        try {
            IntentLaunchManager.clearAllTemporaryOverrides()
            Timber.d("[PluviaApp]: Cleared temporary config overrides from previous session")
        } catch (e: Exception) {
            Timber.e(e, "[PluviaApp]: Failed to clear temporary config overrides")
        }

        // Initialize PostHog Analytics (skip when key is unset/blank)
        val postHogKey = BuildConfig.POSTHOG_API_KEY
        if (postHogKey.isNotBlank() && postHogKey != "unset") {
            val postHogConfig = PostHogAndroidConfig(
                apiKey = postHogKey,
                host = BuildConfig.POSTHOG_HOST,
            ).apply {
                personProfiles = PersonProfiles.ALWAYS
            }
            PostHogAndroid.setup(this, postHogConfig)
        }

        // Initialize Supabase client
        try {
            // #region agent log
            DebugSessionLogger.log(
                "H_supabase_runtime",
                "PluviaApp.kt:initSupabase",
                "BuildConfig Supabase values at runtime",
                mapOf(
                    "urlValue" to BuildConfig.SUPABASE_URL,
                    "urlIsUnset" to (BuildConfig.SUPABASE_URL == "unset"),
                    "urlBlank" to BuildConfig.SUPABASE_URL.isBlank(),
                    "keyLength" to BuildConfig.SUPABASE_KEY.length,
                    "keyIsUnset" to (BuildConfig.SUPABASE_KEY == "unset"),
                    "keyBlank" to BuildConfig.SUPABASE_KEY.isBlank(),
                )
            )
            // #endregion
            initSupabase()
            Timber.d("Supabase client initialized with URL: ${BuildConfig.SUPABASE_URL}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Supabase client: ${e.message}")
            // supabase stays null; app continues without feedback/supporters
        }
    }

    companion object {
        @JvmField
        val events: EventDispatcher = EventDispatcher()
        internal var onDestinationChangedListener: NavChangedListener? = null

        // TODO: find a way to make this saveable, this is terrible (leak that memory baby)
        internal var xEnvironment: XEnvironment? = null
        internal var xServerView: XServerView? = null
        var inputControlsView: InputControlsView? = null
        var inputControlsManager: InputControlsManager? = null
        var touchpadView: TouchpadView? = null

        @JvmField
        var isOverlayPaused: Boolean = false

        // Supabase client for game feedback (null when URL/key unset — app still runs without it)
        var supabase: SupabaseClient? = null
            private set

        // Initialize Supabase client; no-op when URL/key unset so app does not crash
        @OptIn(SupabaseInternal::class)
        fun initSupabase() {
            Timber.d("Initializing Supabase client with URL: ${BuildConfig.SUPABASE_URL}")
            if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_URL == "unset" || BuildConfig.SUPABASE_KEY.isBlank() || BuildConfig.SUPABASE_KEY == "unset") {
                Timber.w("Supabase URL or key unset — feedback/supporters features disabled")
                return
            }

            supabase = createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_KEY
            ) {
                Timber.d("Configuring Supabase client")
                httpConfig {
                    Timber.d("Setting up HTTP timeouts")
                    install(HttpTimeout) {
                        requestTimeoutMillis = 30_000   // overall call
                        connectTimeoutMillis = 15_000   // TCP handshake / TLS
                        socketTimeoutMillis  = 30_000   // idle socket
                    }
                }
                install(Postgrest)
                Timber.d("Postgrest plugin installed")
            }
        }
    }
}
