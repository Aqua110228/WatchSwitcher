package nodomain.freeyourgadget.gadgetbridge.activities.charts.vico

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

/**
 * A message shown in place of a chart, e.g. "no data" or a load error.
 */
@Composable
internal fun ChartMessage(text: String, modifier: Modifier = Modifier) {
    val theme = ChartTheme.from(LocalContext.current)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text = text, style = TextStyle(color = Color(theme.secondaryTextColor)))
    }
}
