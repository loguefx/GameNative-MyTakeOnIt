package app.gamenative.compat

import kotlinx.serialization.Serializable

/**
 * Compatibility DB entry: verification status, known-good profile, minimum requirements.
 * Never suggest "try random settings"; only known-good profile or explain unsupported.
 */
@Serializable
data class CompatibilityEntry(
    val steamAppId: Long,
    val requiredBackend: String = "dx11",
    val requiredRuntimeVersions: Map<String, String> = emptyMap(),
    val knownGoodProfileId: String? = null,
    val knownGoodProfileVersion: String? = null,
    val minRamMb: Int = 0,
    val gpuClass: String? = null,
    val knownIssues: String? = null,
    val workarounds: String? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.UNKNOWN,
    val dx12Verified: Boolean = false,
    val overlayVerified: Boolean = false,
) {
    @Serializable
    enum class VerificationStatus {
        VERIFIED,
        BOOTS_ONLY,
        UNSUPPORTED,
        UNKNOWN,
    }
}
