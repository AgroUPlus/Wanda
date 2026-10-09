package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A button that reacts the way the rows in a sheet do: the instant a finger lands its corners round
 * off toward a pill and its content shrinks, on one spring. [height] sets its size; the rounding
 * follows it.
 */
@Composable
internal fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge
) {
    val morph = rememberPressMorph()
    Surface(
        onClick = onClick,
        shape = morph.shape(rest = 18.dp, pressed = height / 2),
        color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = height).trackPress(morph)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.pressShrink(morph).padding(horizontal = 20.dp)
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
            Text(text, style = textStyle)
        }
    }
}
