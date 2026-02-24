package app.gamenative.regression

import app.gamenative.preflight.PreflightResult
import app.gamenative.preflight.PreflightCode
import app.gamenative.profile.ProfileStore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for deterministic stability: preflight, compatibility contract.
 */
class PreflightRegressionTest {

    @Test
    fun preflightResult_Ok_hasNoBlock() {
        val result = PreflightResult.Ok
        assertTrue(result is PreflightResult.Ok)
    }

    @Test
    fun preflightResult_Blocked_hasReasonAndCode() {
        val result = PreflightResult.Blocked("Insufficient RAM", PreflightCode.INSUFFICIENT_RAM)
        assertTrue(result is PreflightResult.Blocked)
        assertEquals("Insufficient RAM", (result as PreflightResult.Blocked).reason)
        assertEquals(PreflightCode.INSUFFICIENT_RAM, (result as PreflightResult.Blocked).code)
    }

    @Test
    fun profileStore_defaultProfile_hasDx11() {
        val profile = ProfileStore.defaultProfile("STEAM_123")
        assertEquals(app.gamenative.profile.LaunchProfile.GraphicsBackend.DX11, profile.graphicsBackend)
        assertEquals(60, profile.fpsCap)
    }
}
