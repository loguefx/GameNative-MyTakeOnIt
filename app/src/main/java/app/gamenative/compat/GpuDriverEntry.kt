package app.gamenative.compat

import kotlinx.serialization.Serializable

/**
 * GPU/driver compatibility: allow DX12, allow DXVK, or blacklist with reason.
 * Preflight consults this when profile requests DX12.
 */
@Serializable
data class GpuDriverEntry(
    val gpuNamePattern: String,
    val driverVersionPattern: String? = null,
    val allowDx12: Boolean = false,
    val allowDxvk: Boolean = true,
    val blacklisted: Boolean = false,
    val blacklistReason: String? = null,
)
