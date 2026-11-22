// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one card a new person sees on Home: three ways in, a dismiss, and then never again.
 *
 * It is a card rather than a tour, so it cannot cover a control, and every row is a plain clickable
 * that goes where its own label says. Nothing here asks for anything: no permission, no account, no
 * question. Sizes are not fixed, because three rows of wrapping copy at a 2.0× font scale is exactly
 * what a fixed height would cut off.
 */
@Composable
internal fun FirstRunCard(
    onPresets: () -> Unit,
    onDescribe: () -> Unit,
    onLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        stringResource(R.string.first_run_title),
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.first_run_body),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 32dp is where M3's own 40dp ripple box starts missing: the glyph stays 18dp, the
                // target is the minimum a finger needs.
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(R.string.first_run_a11y_dismiss),
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            FirstRunRow(
                Icons.Outlined.Bookmarks,
                stringResource(R.string.first_run_presets_title),
                stringResource(R.string.first_run_presets_body),
                onPresets,
            )
            FirstRunRow(
                Icons.Outlined.AutoAwesome,
                stringResource(R.string.first_run_describe_title),
                stringResource(R.string.first_run_describe_body),
                onDescribe,
            )
            FirstRunRow(
                Icons.Outlined.LibraryMusic,
                stringResource(R.string.first_run_library_title),
                stringResource(R.string.first_run_library_body),
                onLibrary,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(R.string.first_run_dismiss),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FirstRunRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
