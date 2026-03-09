package app.gamenative.utils

/**
 * One entry from the merged Wine/Proton manifest (remote + wine_proton_versions.json).
 * Used by the Game Settings Compatibility Layer version picker.
 */
data class MergedVersionEntry(
    val id: String,
    val displayName: String,
    val sourceGroup: String,
    val sourceType: String,
    val sizeMb: Float,
    val isInstalled: Boolean,
    val isLatest: Boolean,
    val isRecommended: Boolean,
    val manifestEntry: ManifestEntry,
)
