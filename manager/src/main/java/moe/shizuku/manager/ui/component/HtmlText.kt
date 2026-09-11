package moe.shizuku.manager.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(html, linkColor) {
        AnnotatedString.fromHtml(html, linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor)))
    }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
    )
}
