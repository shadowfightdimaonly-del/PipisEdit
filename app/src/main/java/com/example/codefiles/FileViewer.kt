package com.example.codefiles

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val KEYWORDS = listOf(
    "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
    "continue", "def", "default", "do", "else", "enum", "export", "extends", "false",
    "final", "finally", "for", "fun", "function", "if", "implements", "import", "in",
    "interface", "is", "lambda", "let", "new", "null", "None", "object", "override",
    "package", "private", "protected", "public", "return", "self", "static", "struct",
    "super", "switch", "this", "throw", "true", "True", "False", "try", "typeof", "val",
    "var", "void", "when", "while", "with", "yield", "from", "pass", "elif", "except",
    "lateinit", "suspend", "data", "sealed", "open", "internal", "namespace", "using"
)

private val HASH_COMMENT = setOf("py", "sh", "bash", "yml", "yaml", "toml", "rb", "properties", "gitignore", "conf")
private val SLASH_COMMENT = setOf(
    "kt", "kts", "java", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp", "cs",
    "go", "rs", "swift", "dart", "php", "gradle", "css", "scss"
)
private val MARKUP = setOf("xml", "html", "htm")
private val DATA = setOf("json")

private fun readFileText(file: File): String {
    if (file.length() > 1024 * 1024) return "Файл слишком большой (больше 1 МБ)."
    return try {
        val text = file.readText()
        if (text.take(1000).contains('\u0000')) "Это бинарный файл, показать его нельзя." else text
    } catch (e: Exception) {
        "Не удалось прочитать файл."
    }
}

private fun highlight(rawText: String, ext: String, dark: Boolean): List<AnnotatedString> {
    val text = rawText.replace("\r\n", "\n")

    val hash = ext in HASH_COMMENT
    val slash = ext in SLASH_COMMENT
    val markup = ext in MARKUP
    val data = ext in DATA
    val styled = hash || slash || markup || data

    val ann = buildAnnotatedString {
        append(text)
        if (styled) {
            val commentColor = if (dark) Color(0xFF6A9955) else Color(0xFF6A737D)
            val stringColor = if (dark) Color(0xFFCE9178) else Color(0xFF0A7D32)
            val numberColor = if (dark) Color(0xFFB5CEA8) else Color(0xFF1565C0)
            val keywordColor = if (dark) Color(0xFFC586C0) else Color(0xFF8E24AA)

            val parts = mutableListOf<String>()
            if (hash) parts.add("#[^\\n]*")
            if (slash) {
                parts.add("//[^\\n]*")
                parts.add("/\\*[\\s\\S]*?\\*/")
            }
            if (markup) parts.add("<!--[\\s\\S]*?-->")
            val comment = if (parts.isEmpty()) "(?!)" else parts.joinToString("|")

            val string = "\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"
            val number = "\\b\\d+(?:\\.\\d+)?\\b"
            val keyword = "\\b(?:" + KEYWORDS.joinToString("|") + ")\\b"

            val regex = Regex("($comment)|($string)|($number)|($keyword)")
            for (m in regex.findAll(text)) {
                val color = when {
                    m.groups[1] != null -> commentColor
                    m.groups[2] != null -> stringColor
                    m.groups[3] != null -> numberColor
                    else -> if (data || markup) null else keywordColor
                }
                if (color != null) {
                    addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1)
                }
            }
        }
    }

    val result = mutableListOf<AnnotatedString>()
    var start = 0
    while (true) {
        val i = text.indexOf('\n', start)
        if (i < 0) {
            result.add(ann.subSequence(start, text.length))
            break
        }
        result.add(ann.subSequence(start, i))
        start = i + 1
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewer(file: File, onBack: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ext = file.extension.lowercase()
    val text = remember(file) { readFileText(file) }
    val lines = remember(text, dark) { highlight(text, ext, dark) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .horizontalScroll(rememberScrollState())
        ) {
            itemsIndexed(lines) { index, line ->
                Row {
                    Text(
                        text = (index + 1).toString().padStart(4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                    )
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        softWrap = false,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }
    }
}