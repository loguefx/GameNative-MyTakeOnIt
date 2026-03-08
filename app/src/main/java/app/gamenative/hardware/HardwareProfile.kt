package app.gamenative.hardware

import kotlinx.serialization.Serializable

/**
 * Snapshot of device hardware used by GameConfigRecommender for resolution scale,
 * DXVK memory budget, P-core mask, Vulkan tier, and display caps.
 * Cached in DataStore; invalidated on app update or when Turnip/Mesa (graphics driver) is installed or switched.
 */
@Serializable
data class HardwareProfile(
    // CPU
    val cpuSoC: String,
    val cpuCores: Int,
    val cpuPCoreCount: Int,
    val cpuECoreCount: Int,
    val cpuPCoreMask: String,
    val cpuMaxFreqMhz: Int,

    // GPU
    val gpuVendor: GpuVendor,
    val gpuModel: String,
    val gpuOpenGLES: String,
    val gpuVulkanVersion: String,
    val gpuDriverVersion: String,
    val supportsVulkan13: Boolean,
    val supportsMeshShaders: Boolean,
    val supportsRayTracing: Boolean,

    // Memory
    val totalRamMb: Int,
    val availableRamMb: Int,
    val storageType: StorageType,

    // Display
    val displayRefreshHz: Int,
    val displayWidthPx: Int,
    val displayHeightPx: Int,

    // Thermal
    val thermalHeadroom: Float,
    val sustainedPerformanceSupported: Boolean,
) {
    val ramGb: Int get() = totalRamMb / 1024
}

@Serializable
enum class GpuVendor {
    ADRENO,
    MALI,
    POWERVR,
    UNKNOWN,
}

@Serializable
enum class StorageType {
    UFS_3_1,
    UFS_3_0,
    UFS_2_2,
    EMMC,
    UNKNOWN,
}
