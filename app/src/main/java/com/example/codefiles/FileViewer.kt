package com.example.codefiles

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- Настройки (размер шрифта, перенос строк) ----------

object Prefs {
    var fontSize by mutableIntStateOf(13)
    var wrap by mutableStateOf(true)

    fun load(context: Context) {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        fontSize = p.getInt("fontSize", 13)
        wrap = p.getBoolean("wrap", true)
    }

    fun save(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putInt("fontSize", fontSize)
            .putBoolean("wrap", wrap)
            .apply()
    }
}

// ---------- Подсветка синтаксиса ----------

private const val MAX_VIEW = 20L * 1024 * 1024
private const val MAX_EDIT = 400L * 1024

class Lang(val hash: Boolean, val slash: Boolean, val markup: Boolean)

private val HASH_EXT = setOf(
    "py", "sh", "bash", "yml", "yaml", "toml", "rb", "properties", "gitignore",
    "conf", "ini", "cfg", "env", "mcfunction"
)
private val SLASH_EXT = setOf(
    "kt", "kts", "java", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp", "cs",
    "go", "rs", "swift", "dart", "php", "gradle", "css", "scss", "json"
)
private val MARKUP_EXT = setOf("xml", "html", "htm")

private val KEYWORDS = hashSetOf(
    "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
    "continue", "def", "default", "do", "else", "enum", "export", "extends", "false",
    "final", "finally", "for", "fun", "function", "if", "implements", "import", "in",
    "interface", "is", "lambda", "let", "new", "null", "None", "object", "override",
    "package", "private", "protected", "public", "return", "self", "static", "struct",
    "super", "switch", "this", "throw", "true", "True", "False", "try", "typeof", "val",
    "var", "void", "when", "while", "with", "yield", "from", "pass", "elif", "except",
    "lateinit", "suspend", "data", "sealed", "open", "internal", "namespace", "using"
)

private fun langFor(ext: String): Lang? = when {
    ext in HASH_EXT -> Lang(hash = true, slash = false, markup = false)
    ext in SLASH_EXT -> Lang(hash = false, slash = true, markup = false)
    ext in MARKUP_EXT -> Lang(hash = false, slash = false, markup = true)
    else -> null
}

private fun colorOf(kind: Int): Color = when (kind) {
    1 -> Color(0xFF6A9955)
    2 -> Color(0xFFCE9178)
    3 -> Color(0xFFB5CEA8)
    else -> Color(0xFFC586C0)
}

/** Разбор одной строки. kind: 1 комментарий, 2 строка, 3 число, 4 ключевое слово.
 *  Возвращает true, если строка закончилась внутри блочного комментария. */
private fun scanLine(
    s: String,
    lang: Lang,
    startInBlock: Boolean,
    emit: (Int, Int, Int) -> Unit
): Boolean {
    val n = s.length
    val closer = if (lang.markup) "-->" else "*/"
    var i = 0
    if (startInBlock) {
        val j = s.indexOf(closer)
        if (j < 0) {
            if (n > 0) emit(0, n, 1)
            return true
        }
        emit(0, j + closer.length, 1)
        i = j + closer.length
    }
    while (i < n) {
        val c = s[i]
        if (lang.slash && c == '/' && i + 1 < n && s[i + 1] == '/') {
            emit(i, n, 1)
            return false
        }
        if (lang.slash && c == '/' && i + 1 < n && s[i + 1] == '*') {
            val j = s.indexOf("*/", i + 2)
            if (j < 0) {
                emit(i, n, 1)
                return true
            }
            emit(i, j + 2, 1)
            i = j + 2
            continue
        }
        if (lang.markup && c == '<' && s.startsWith("<!--", i)) {
            val j = s.indexOf("-->", i + 4)
            if (j < 0) {
                emit(i, n, 1)
                return true
            }
            emit(i, j + 3, 1)
            i = j + 3
            continue
        }
        if (lang.hash && c == '#') {
            emit(i, n, 1)
            return false
        }
        if (c == '"' || (c == '\'' && !lang.markup)) {
            var j = i + 1
            while (j < n && s[j] != c) {
                if (s[j] == '\\') j++
                j++
            }
            val end = minOf(j + 1, n)
            emit(i, end, 2)
            i = end
            continue
        }
        if (c.isDigit()) {
            var j = i + 1
            while (j < n && (s[j].isLetterOrDigit() || s[j] == '.')) j++
            emit(i, j, 3)
            i = j
            continue
        }
        if (c.isLetter() || c == '_') {
            var j = i + 1
            while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
            if (!lang.markup && KEYWORDS.contains(s.substring(i, j))) emit(i, j, 4)
            i = j
            continue
        }
        i++
    }
    return false
}

class Doc(
    val text: String,
    val starts: IntArray,
    val inBlock: BooleanArray,
    val lang: Lang?
) {
    val lineCount: Int get() = starts.size
}

private fun sliceLine(text: String, starts: IntArray, i: Int): String {
    val s = starts[i]
    val e = if (i + 1 < starts.size) starts[i + 1] - 1 else text.length
    return text.substring(s, maxOf(s, e))
}

fun buildDoc(raw: String, ext: String): Doc {
    val text = if (raw.indexOf('\r') >= 0) raw.replace("\r\n", "\n") else raw
    val lang = langFor(ext)
    var count = 1
    for (ch in text) if (ch == '\n') count++
    val starts = IntArray(count)
    var k = 1
    for (i in text.indices) {
        if (text[i] == '\n') {
            starts[k] = i + 1
            k++
        }
    }
    val inBlock = BooleanArray(count)
    if (lang != null) {
        var state = false
        for (l in 0 until count) {
            inBlock[l] = state
            state = scanLine(sliceLine(text, starts, l), lang, state) { _, _, _ -> }
        }
    }
    return Doc(text, starts, inBlock, lang)
}

private fun styledLine(doc: Doc, i: Int): AnnotatedString {
    var line = sliceLine(doc.text, doc.starts, i)
    if (line.length > 3000) line = line.substring(0, 3000) + " …"
    val lang = doc.lang ?: return AnnotatedString(line)
    return buildAnnotatedString {
        append(line)
        scanLine(line, lang, doc.inBlock[i]) { s, e, k ->
            addStyle(SpanStyle(color = colorOf(k)), s, minOf(e, line.length))
        }
    }
}

private fun highlightAll(text: String, ext: String): AnnotatedString {
    val lang = langFor(ext) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var state = false
        var pos = 0
        while (pos <= text.length) {
            var e = text.indexOf('\n', pos)
            if (e < 0) e = text.length
            val base = pos
            state = scanLine(text.substring(pos, e), lang, state) { s, en, k ->
                addStyle(SpanStyle(color = colorOf(k)), base + s, base + en)
            }
            pos = e + 1
        }
    }
}

private fun loadDoc(file: File): Pair<Doc?, String?> {
    if (file.length() > MAX_VIEW) return Pair(null, "Файл больше 20 МБ, он слишком большой для просмотра.")
    return try {
        val bytes = file.readBytes()
        val head = minOf(bytes.size, 2000)
        var binary = false
        for (i in 0 until head) {
            if (bytes[i].toInt() == 0) {
                binary = true
                break
            }
        }
        if (binary) {
            Pair(null, "Это бинарный файл, показать его нельзя.")
        } else {
            Pair(buildDoc(String(bytes, Charsets.UTF_8), file.extension.lowercase()), null)
        }
    } catch (e: Throwable) {
        Pair(null, "Не удалось прочитать файл.")
    }
}

// ---------- Экран просмотра / редактирования ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewer(file: File, onBack: () -> Unit, editable: Boolean = true) {
    val context = LocalContext.current
    val ext = remember(file) { file.extension.lowercase() }
    var doc by remember(file) { mutableStateOf<Doc?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    var editing by remember(file) { mutableStateOf(false) }
    var editText by remember(file) { mutableStateOf("") }
    var askDiscard by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        val result = withContext(Dispatchers.IO) { loadDoc(file) }
        doc = result.first
        error = result.second
    }

    val loaded = doc
    val dirty = editing && loaded != null && editText != loaded.text

    fun requestBack() {
        if (dirty) askDiscard = true
        else if (editing) editing = false
        else onBack()
    }

    fun save() {
        try {
            file.writeText(editText)
            doc = buildDoc(editText, ext)
            editing = false
            Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler { requestBack() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = barColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tight = PaddingValues(horizontal = 10.dp)
                TextButton(
                    onClick = {
                        if (Prefs.fontSize > 8) {
                            Prefs.fontSize -= 1
                            Prefs.save(context)
                        }
                    },
                    contentPadding = tight
                ) { Text("A−") }
                Text("${Prefs.fontSize}", color = PurpleLight)
                TextButton(
                    onClick = {
                        if (Prefs.fontSize < 28) {
                            Prefs.fontSize += 1
                            Prefs.save(context)
                        }
                    },
                    contentPadding = tight
                ) { Text("A+") }
                TextButton(
                    onClick = {
                        Prefs.wrap = !Prefs.wrap
                        Prefs.save(context)
                    },
                    contentPadding = tight
                ) {
                    Text("Перенос", color = if (Prefs.wrap) Purple else Color.Gray)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (editable && loaded != null) {
                    if (editing) {
                        TextButton(onClick = { save() }, contentPadding = tight) { Text("Сохранить") }
                    } else {
                        TextButton(
                            onClick = {
                                if (file.length() > MAX_EDIT) {
                                    Toast.makeText(
                                        context,
                                        "Файл больше 400 КБ, править его в приложении нельзя",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    editText = loaded.text
                                    editing = true
                                }
                            },
                            contentPadding = tight
                        ) { Text("Править") }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val err = error
                if (err != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { Text(err) }
                } else if (loaded == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { CircularProgressIndicator(color = Purple) }
                } else if (editing) {
                    Editor(text = editText, ext = ext, onChange = { editText = it })
                } else {
                    CodeList(loaded)
                }
            }
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Выйти без сохранения?") },
            text = { Text("Изменения в файле будут потеряны.") },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    editing = false
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false }) { Text("Остаться") }
            }
        )
    }
}

@Composable
private fun CodeList(doc: Doc) {
    val size = Prefs.fontSize
    val wrap = Prefs.wrap
    val digits = doc.lineCount.toString().length
    val hScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll))
    ) {
        items(count = doc.lineCount) { i ->
            val line = remember(doc, i) { styledLine(doc, i) }
            Row {
                Text(
                    text = (i + 1).toString().padStart(digits),
                    fontFamily = FontFamily.Monospace,
                    fontSize = size.sp,
                    lineHeight = (size * 1.35f).sp,
                    color = Color(0xFF7C6A99),
                    modifier = Modifier.padding(start = 8.dp, end = 10.dp)
                )
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = size.sp,
                    lineHeight = (size * 1.35f).sp,
                    softWrap = wrap,
                    modifier = if (wrap) Modifier.weight(1f).padding(end = 8.dp)
                    else Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun Editor(text: String, ext: String, onChange: (String) -> Unit) {
    val size = Prefs.fontSize
    val wrap = Prefs.wrap
    val transformation = remember(ext) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText =
                TransformedText(highlightAll(text.text, ext), OffsetMapping.Identity)
        }
    }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(vScroll)
            .then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll))
            .padding(8.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = size.sp,
                lineHeight = (size * 1.35f).sp,
                color = Color(0xFFEDE0FF)
            ),
            cursorBrush = SolidColor(Purple),
            visualTransformation = transformation,
            modifier = (if (wrap) Modifier.fillMaxWidth() else Modifier).heightIn(min = 300.dp)
        )
    }
}