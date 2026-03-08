package app.gamenative.config

/**
 * Hardcoded fixes for games that crash or misbehave without specific Proton/DX/env settings.
 * Override recommender defaults; can be extended later via remote JSON for new fixes without app update.
 */
object KnownGameFixes {

    data class GameFix(
        val appId: Int,
        val gameName: String,
        val protonVersion: ProtonVersion? = null,
        val dxVersion: DxVersion? = null,
        val requiresD8VK: Boolean = false,
        val resolutionScale: Float? = null,
        val fpsLimit: Int? = null,
        val extraEnvVars: Map<String, String> = emptyMap(),
        val launchArgs: String = "",
        val reason: String = "",
    )

    private val fixes = mapOf(
        1245620 to GameFix(
            appId = 1245620,
            gameName = "Elden Ring",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            extraEnvVars = mapOf(
                "PROTON_ENABLE_NVAPI" to "0",
                "VKD3D_CONFIG" to "pipeline_library_app_cache,no_upload_hvv,force_static_cbv",
            ),
            reason = "Proton 9.0 more stable than 10.0 for DX12 on Adreno",
        ),
        271590 to GameFix(
            appId = 271590,
            gameName = "GTA V",
            protonVersion = ProtonVersion.PROTON_8_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                "DXVK_ASYNC" to "1",
                "PROTON_NO_ESYNC" to "0",
            ),
            reason = "DX11 mode more stable than DX12 on Adreno, Proton 8.0 recommended",
        ),
        1091500 to GameFix(
            appId = 1091500,
            gameName = "Cyberpunk 2077",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX12,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "VKD3D_CONFIG" to "pipeline_library_app_cache,no_upload_hvv,force_static_cbv",
                "PROTON_HIDE_NVIDIA_GPU" to "0",
                "VKD3D_SHADER_MODEL" to "6_5",
            ),
            reason = "Cyberpunk requires DX12, 0.75x scale recommended for stable FPS on mobile",
        ),
        1174180 to GameFix(
            appId = 1174180,
            gameName = "Red Dead Redemption 2",
            protonVersion = ProtonVersion.PROTON_8_0,
            dxVersion = DxVersion.DX11,
            resolutionScale = 0.75f,
            extraEnvVars = mapOf(
                "DXVK_ASYNC" to "1",
                "WINEDLLOVERRIDES" to "vulkan-1=n;winevulkan=n",
            ),
            reason = "DX11 performs better than Vulkan native on Adreno for RDR2",
        ),
        292030 to GameFix(
            appId = 292030,
            gameName = "The Witcher 3",
            protonVersion = ProtonVersion.PROTON_9_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf("DXVK_ASYNC" to "1"),
            reason = "DX11 most compatible version for Witcher 3 on Wine",
        ),
        730 to GameFix(
            appId = 730,
            gameName = "CS2",
            protonVersion = ProtonVersion.PROTON_10_0,
            dxVersion = DxVersion.DX11,
            extraEnvVars = mapOf(
                "PROTON_ENABLE_NVAPI" to "0",
                "DXVK_FRAME_RATE" to "60",
            ),
            reason = "CS2 works well on Proton 10.0 with DX11",
        ),
    )

    fun get(appId: Int): GameFix? = fixes[appId]
}
