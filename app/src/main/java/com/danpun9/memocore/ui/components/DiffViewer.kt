package com.danpun9.memocore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextDiffViewer(
    oldText: String,
    newText: String,
    modifier: Modifier = Modifier
) {
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    
    // Very basic line-by-line diff.
    // For a production app, use a proper diff algorithm (like Myers).
    // This is a naive visualization for MVP.
    
    val maxLines = maxOf(oldLines.size, newLines.size)
    
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        for (i in 0 until maxLines) {
            val oldLine = oldLines.getOrNull(i)
            val newLine = newLines.getOrNull(i)
            
            if (oldLine == newLine) {
                 // Unchanged
                 if (oldLine != null) {
                    Text(
                        text = "  $oldLine",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                 }
            } else {
                // Changed (Naive: assumes strict line mapping)
                // If old line exists, show as deleted
                if (oldLine != null) {
                    Text(
                        text = "- $oldLine",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFB00020)) // Error Red
                            .padding(horizontal = 4.dp)
                    )
                }
                
                // If new line exists, show as added
                if (newLine != null) {
                     Text(
                        text = "+ $newLine",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF00FF00)) // Green
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
