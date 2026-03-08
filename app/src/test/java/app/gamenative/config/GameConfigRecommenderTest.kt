package app.gamenative.config

import app.gamenative.hardware.GpuVendor
import app.gamenative.hardware.HardwareProfile
import app.gamenative.hardware.StorageType
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Resolution scaling depends on isHighEndAdreno: 730/740/750 get 1.0f at 8GB+ RAM,
 * 640/650/660 do not. Regex must match 7[3-9][0-9] only.
 */
class GameConfigRecommenderTest {

    private fun profile(gpuModel: String): HardwareProfile = HardwareProfile(
        cpuSoC = "Test SoC",
        cpuCores = 8,
        cpuPCoreCount = 4,
        cpuECoreCount = 4,
        cpuPCoreMask = "0xF0",
        cpuMaxFreqMhz = 2800,
        gpuVendor = GpuVendor.ADRENO,
        gpuModel = gpuModel,
        gpuOpenGLES = "3.2",
        gpuVulkanVersion = "1.3.255",
        gpuDriverVersion = "3.2",
        supportsVulkan13 = true,
        supportsMeshShaders = false,
        supportsRayTracing = false,
        totalRamMb = 8192,
        availableRamMb = 4096,
        storageType = StorageType.UFS_3_0,
        displayRefreshHz = 120,
        displayWidthPx = 1080,
        displayHeightPx = 1920,
        thermalHeadroom = 1.0f,
        sustainedPerformanceSupported = false,
    )

    @Test
    fun isHighEndAdreno_matches_730_740_750() {
        val recommender = GameConfigRecommender
        assertTrue(recommender.isHighEndAdreno(profile("Adreno (TM) 730")))
        assertTrue(recommender.isHighEndAdreno(profile("Adreno (TM) 740")))
        assertTrue(recommender.isHighEndAdreno(profile("Adreno (TM) 750")))
        assertTrue(recommender.isHighEndAdreno(profile("Adreno 730")))
        assertTrue(recommender.isHighEndAdreno(profile("Adreno 799")))
    }

    @Test
    fun isHighEndAdreno_rejects_640_650_660() {
        val recommender = GameConfigRecommender
        assertFalse(recommender.isHighEndAdreno(profile("Adreno (TM) 640")))
        assertFalse(recommender.isHighEndAdreno(profile("Adreno (TM) 650")))
        assertFalse(recommender.isHighEndAdreno(profile("Adreno (TM) 660")))
        assertFalse(recommender.isHighEndAdreno(profile("Adreno 630")))
        assertFalse(recommender.isHighEndAdreno(profile("Adreno 669")))
    }
}
