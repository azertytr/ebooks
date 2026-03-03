package com.ebooks.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebooks.reader.viewmodel.FontFamily
import com.ebooks.reader.viewmodel.ReaderSettings
import com.ebooks.reader.viewmodel.ReaderThemeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Reading Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

            // ── Theme Selection ──────────────────────────────────────────────
            SectionLabel("Theme")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeOption(
                    label = "Light",
                    bg = Color.White,
                    text = Color(0xFF222222),
                    selected = settings.themeOption == ReaderThemeOption.LIGHT,
                    onClick = { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.LIGHT)) }
                )
                ThemeOption(
                    label = "Dark",
                    bg = Color(0xFF1a1a2e),
                    text = Color(0xFFe0e0e0),
                    selected = settings.themeOption == ReaderThemeOption.DARK,
                    onClick = { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.DARK)) }
                )
                ThemeOption(
                    label = "Sepia",
                    bg = Color(0xFFF3EAD3),
                    text = Color(0xFF3b2f1e),
                    selected = settings.themeOption == ReaderThemeOption.SEPIA,
                    onClick = { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.SEPIA)) }
                )
                ThemeOption(
                    label = "Night",
                    bg = Color(0xFF0d0d0d),
                    text = Color(0xFFaaaaaa),
                    selected = settings.themeOption == ReaderThemeOption.NIGHT,
                    onClick = { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.NIGHT)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Font Size ──────────────────────────────────────────────────
            SectionLabel("Font Size")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onSettingsChanged(settings.copy(fontSize = (settings.fontSize - 1).coerceAtLeast(12))) }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease font size")
                }
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(fontSize = it.toInt())) },
                    valueRange = 12f..32f,
                    steps = 19,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onSettingsChanged(settings.copy(fontSize = (settings.fontSize + 1).coerceAtMost(32))) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase font size")
                }
                Text(
                    text = "${settings.fontSize}px",
                    modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // ── Line Height ────────────────────────────────────────────────
            SectionLabel("Line Spacing")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FormatLineSpacing, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Slider(
                    value = settings.lineHeight,
                    onValueChange = { onSettingsChanged(settings.copy(lineHeight = it)) },
                    valueRange = 1.0f..3.0f,
                    steps = 19,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format("%.1f", settings.lineHeight),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(32.dp)
                )
            }

            // ── Font Family ────────────────────────────────────────────────
            SectionLabel("Font")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FontFamily.values().forEach { family ->
                    FilterChip(
                        selected = settings.fontFamily == family,
                        onClick = { onSettingsChanged(settings.copy(fontFamily = family)) },
                        label = { Text(family.displayName, fontSize = 12.sp) }
                    )
                }
            }

            // ── Paragraph Indent ───────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatIndentIncrease, contentDescription = null,
                        modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Paragraph Indent")
                }
                Switch(
                    checked = settings.paragraphIndent,
                    onCheckedChange = { onSettingsChanged(settings.copy(paragraphIndent = it)) }
                )
            }

            // ── Keep Screen On ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LightMode, contentDescription = null,
                        modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keep Screen On")
                }
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { onSettingsChanged(settings.copy(keepScreenOn = it)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeOption(
    label: String,
    bg: Color,
    text: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bg)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
