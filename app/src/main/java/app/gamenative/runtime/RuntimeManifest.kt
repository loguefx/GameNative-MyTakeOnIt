package app.gamenative.runtime

import android.content.Context
import kotlinx.serialization.Serializable

/**
 * Pinned runtime bundle manifest: exact versions and paths for Box64, Wine, DXVK, VKD3D.
 * Same APK/release = same stack = same behavior on same device.
 */
@Serializable
data class RuntimeManifest(
    val box64Version: String,
    val wineVersion: String,
    val dxvkVersion: String,
    val vkd3dVersion: String,
    val manifestVersion: Int = 1,
) {
    companion object {
        fun fromContext(context: Context): RuntimeManifest {
            return RuntimeManifest(
                box64Version = com.winlator.core.DefaultVersion.BOX64,
                wineVersion = com.winlator.core.DefaultVersion.WINE_VERSION,
                dxvkVersion = com.winlator.core.DefaultVersion.DXVK,
                vkd3dVersion = com.winlator.core.DefaultVersion.VKD3D,
            )
        }
    }
}
