package cn.edu.swun.swun_ehall.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val cs = MiuixTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        markdown.replace("\r\n", "\n").lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(8.dp))
                line.startsWith("### ") -> Text(
                    inlineMarkdown(line.removePrefix("### ").trim()),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                line.startsWith("## ") -> Text(
                    inlineMarkdown(line.removePrefix("## ").trim()),
                    style = MiuixTheme.textStyles.title3,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                line.startsWith("# ") -> Text(
                    inlineMarkdown(line.removePrefix("# ").trim()),
                    style = MiuixTheme.textStyles.title2,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row(Modifier.padding(bottom = 4.dp)) {
                    Text("•", color = cs.primary, modifier = Modifier.width(16.dp))
                    Text(inlineMarkdown(line.drop(2).trim()), modifier = Modifier.weight(1f))
                }
                Regex("""^\d+\.\s+""").containsMatchIn(line) -> Row(Modifier.padding(bottom = 4.dp)) {
                    val mark = Regex("""^(\d+)\.\s+""").find(line)!!
                    Text("${mark.groupValues[1]}.", color = cs.primary, modifier = Modifier.width(22.dp))
                    Text(inlineMarkdown(line.removePrefix(mark.value).trim()), modifier = Modifier.weight(1f))
                }
                else -> Text(inlineMarkdown(line.trim()), modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

internal fun inlineMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("**", i) -> {
                val end = source.indexOf("**", i + 2)
                if (end < 0) {
                    append(source.substring(i))
                    return@buildAnnotatedString
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                append(source.substring(i + 2, end))
                pop()
                i = end + 2
            }
            source[i] == '`' -> {
                val end = source.indexOf('`', i + 1)
                if (end < 0) {
                    append(source.substring(i))
                    return@buildAnnotatedString
                }
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                append(source.substring(i + 1, end))
                pop()
                i = end + 1
            }
            else -> {
                append(source[i])
                i++
            }
        }
    }
}
