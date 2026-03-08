package app.gamenative.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.ui.theme.PluviaTypography
import app.gamenative.ui.theme.gnNeonOrange
import app.gamenative.ui.theme.gnNeonTeal
import app.gamenative.ui.theme.gnStatusError
import app.gamenative.ui.theme.gnTextSecondary
import app.gamenative.proton.ProtonTier
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Task 5 — ProtonDB tier badge for game cards and settings.
 * Uses existing gn_* colors; does not show for UNKNOWN or PENDING.
 */
@Composable
fun ProtonBadge(
    tier: ProtonTier,
    modifier: Modifier = Modifier,
) {
    if (tier == ProtonTier.UNKNOWN || tier == ProtonTier.PENDING) return

    val (label, color) = when (tier) {
        ProtonTier.PLATINUM -> "PLATINUM" to gnNeonTeal
        ProtonTier.GOLD -> "GOLD" to gnNeonOrange
        ProtonTier.SILVER -> "SILVER" to gnTextSecondary
        ProtonTier.BRONZE -> "BRONZE" to gnNeonOrange.copy(alpha = 0.6f)
        ProtonTier.BORKED -> "BORKED" to gnStatusError
        else -> return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = PluviaTypography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.08.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
    }
}
