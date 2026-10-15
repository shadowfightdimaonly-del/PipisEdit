package com.example.codefiles

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

val Purple = Color(0xFF9C4DFF)
val PurpleLight = Color(0xFFD0B0FF)
val PurpleDark = Color(0xFF3A1A66)

val AppColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFEDE0FF),
    surface = Color.Black,
    onSurface = Color(0xFFEDE0FF),
    onSurfaceVariant = PurpleLight,
    surfaceContainerHigh = Color(0xFF14091F),
    outline = Purple
)

val ARCHIVE_EXT = setOf("zip", "mcaddon", "mcpack", "mcworld", "mctemplate", "apk", "jar")

val TEXT_EXT = setOf(
    "txt", "md", "log", "csv", "json", "xml", "html", "htm", "css", "scss", "js", "ts",
    "tsx", "jsx", "kt", "kts", "java", "py", "sh", "bash", "yml", "yaml", "toml", "rb",
    "properties", "gitignore", "gitattributes", "conf", "ini", "cfg", "env", "c", "h",
    "cpp", "hpp", "cs", "go", "rs", "swift", "dart", "php", "gradle", "sql", "bat",
    "lang", "mcfunction", "mcmeta"
)

class Entry(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val sub: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun barColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Black,
    scrolledContainerColor = Color.Black,
    titleContentColor = PurpleLight,
    navigationIconContentColor = Purple,
    actionIconContentColor = Purple
)

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    private val legacyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasAccess = checkAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    if (hasAccess) {
                        FileBrowser()
                    } else {
                        PermissionScreen(onRequest = ::requestAccess)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = checkAccess()
    }

    private fun checkAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

// ---------- Файловые операции ----------

fun openWithSystem(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть с помощью"))
    } catch (e: Exception) {
        Toast.makeText(context, "Нет приложения для этого файла", Toast.LENGTH_LONG).show()
    }
}

fun unzipArchive(archive: File): File? {
    val parent = archive.parentFile ?: return null
    val base = archive.nameWithoutExtension
    var target = File(parent, base)
    var n = 1
    while (target.exists()) {
        target = File(parent, "$base ($n)")
        n++
    }
    return try {
        target.mkdirs()
        val safeRoot = target.canonicalPath + File.separator
        ZipFile(archive).use { zip ->
            for (entry in zip.entries().toList()) {
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(safeRoot)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        target
    } catch (e: Exception) {
        null
    }
}

fun loadEntries(dir: File): List<Entry> =
    (dir.listFiles() ?: emptyArray())
        .map { Entry(it, it.name, it.isDirectory, if (it.isDirectory) 0L else it.length()) }
        .sortedWith(compareByDescending<Entry> { it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

suspend fun searchEntries(base: File, query: String): List<Entry> {
    val q = query.lowercase()
    val out = ArrayList<Entry>()
    val stack = ArrayDeque<File>()
    stack.add(base)
    var visited = 0
    val basePath = base.absolutePath
    while (stack.isNotEmpty() && out.size < 300 && visited < 30000) {
        currentCoroutineContext().ensureActive()
        val dir = stack.removeLast()
        visited++
        val kids = dir.listFiles() ?: continue
        for (f in kids) {
            val isDir = f.isDirectory
            if (f.name.lowercase().contains(q)) {
                val rel = (f.parentFile?.absolutePath ?: "").removePrefix(basePath).ifEmpty { "/" }
                out.add(Entry(f, f.name, isDir, if (isDir) 0L else f.length(), rel))
            }
            if (isDir) stack.add(f)
        }
    }
    return out
}

fun uniqueTarget(dir: File, name: String): File {
    var t = File(dir, name)
    if (!t.exists()) return t
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var n = 1
    while (t.exists()) {
        t = File(dir, "$base ($n)$ext")
        n++
    }
    return t
}

fun pasteInto(src: File, destDir: File, move: Boolean): Boolean {
    val s = src.canonicalPath
    val d = destDir.canonicalPath
    if (src.isDirectory && (d == s || d.startsWith(s + File.separator))) return false
    if (move && src.parentFile?.canonicalPath == d) return true
    val target = uniqueTarget(destDir, src.name)
    return if (move) {
        if (src.renameTo(target)) true else src.copyRecursively(target) && src.deleteRecursively()
    } else {
        src.copyRecursively(target)
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "${bytes / (1024 * 1024)} МБ"
}

// ---------- Экраны ----------

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Нужен доступ к файлам", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Включи переключатель «Разрешить управление всеми файлами» и вернись в приложение.",
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onRequest) { Text("Дать доступ") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(root) }
    var opened by remember { mutableStateOf<File?>(null) }
    var archive by remember { mutableStateOf<File?>(null) }
    var menuFor by remember { mutableStateOf<Entry?>(null) }
    var renameFor by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFor by remember { mutableStateOf<File?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var clip by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var searchOn by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Entry>>(emptyList()) }

    LaunchedEffect(current, refresh) {
        loaded = false
        entries = withContext(Dispatchers.IO) { loadEntries(current) }
        loaded = true
    }

    LaunchedEffect(query, current, searchOn) {
        if (searchOn && query.isNotBlank()) {
            delay(300)
            results = withContext(Dispatchers.IO) { searchEntries(current, query.trim()) }
        } else {
            results = emptyList()
        }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun runOp(start: String?, okMsg: String, failMsg: String, work: () -> Boolean) {
        if (start != null) toast(start)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    work()
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) okMsg else failMsg)
            refresh++
        }
    }

    fun createItem(isDir: Boolean) {
        val n = newName.trim()
        if (n.isEmpty() || n.contains('/')) {
            toast("Некорректное имя")
            return
        }
        val f = File(current, n)
        if (f.exists()) {
            toast("Уже существует")
            return
        }
        val ok = try {
            if (isDir) f.mkdirs() else f.createNewFile()
        } catch (e: Exception) {
            false
        }
        toast(if (ok) "Создано" else "Не удалось создать")
        showCreate = false
        refresh++
    }

    val file = opened
    val arc = archive
    if (file != null) {
        FileViewer(file = file, onBack = { opened = null })
    } else if (arc != null) {
        ArchiveViewer(archive = arc, onBack = { archive = null })
    } else {
        BackHandler(enabled = current != root && !searchOn) {
            current = current.parentFile ?: root
        }
        BackHandler(enabled = searchOn) {
            searchOn = false
            query = ""
        }

        val shown = if (searchOn && query.isNotBlank()) results else entries

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        if (searchOn) {
                            val focus = remember { FocusRequester() }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                                cursorBrush = SolidColor(Purple),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                decorationBox = { inner ->
                                    Box {
                                        if (query.isEmpty()) {
                                            Text("Поиск в этой папке…", color = Color.Gray, fontSize = 18.sp)
                                        }
                                        inner()
                                    }
                                }
                            )
                            LaunchedEffect(Unit) { focus.requestFocus() }
                        } else {
                            Text(
                                current.absolutePath,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    navigationIcon = {
                        if (current != root) {
                            IconButton(onClick = {
                                current = current.parentFile ?: root
                                searchOn = false
                                query = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    },
                    actions = {
                        val c = clip
                        if (c != null) {
                            TextButton(onClick = {
                                runOp("Вставка…", "Готово", "Не удалось вставить") {
                                    pasteInto(c.first, current, c.second)
                                }
                                if (c.second) clip = null
                            }) { Text("Вставить", color = Purple) }
                        }
                        IconButton(onClick = {
                            if (searchOn) {
                                searchOn = false
                                query = ""
                            } else {
                                searchOn = true
                            }
                        }) {
                            Icon(
                                if (searchOn) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Поиск"
                            )
                        }
                    },
                    colors = barColors()
                )
            },
            floatingActionButton = {
                if (!searchOn) {
                    FloatingActionButton(
                        onClick = {
                            newName = ""
                            showCreate = true
                        },
                        containerColor = Purple,
                        contentColor = Color.Black
                    ) { Icon(Icons.Filled.Add, contentDescription = "Создать") }
                }
            }
        ) { padding ->
            if (!loaded && !searchOn) {
                Box(modifier = Modifier.fillMaxSize().padding(padding))
            } else if (shown.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (searchOn && query.isNotBlank()) "Ничего не найдено" else "Папка пуста")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(shown, key = { it.file.absolutePath }) { e ->
                        ItemRow(
                            title = e.name,
                            subtitle = e.sub ?: if (e.isDir) null else formatSize(e.size),
                            isDir = e.isDir,
                            onClick = {
                                val ext = e.file.extension.lowercase()
                                if (e.isDir) {
                                    current = e.file
                                    searchOn = false
                                    query = ""
                                } else if (ext in ARCHIVE_EXT) {
                                    openWithSystem(context, e.file)
                                } else if (ext in TEXT_EXT || ext.isEmpty()) {
                                    opened = e.file
                                } else {
                                    openWithSystem(context, e.file)
                                }
                            },
                            onLongClick = { menuFor = e }
                        )
                    }
                }
            }
        }

        // Меню файла / папки
        val m = menuFor
        if (m != null) {
            val isArchive = !m.isDir && m.file.extension.lowercase() in ARCHIVE_EXT
            AlertDialog(
                onDismissRequest = { menuFor = null },
                title = { Text(m.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        if (isArchive) {
                            TextButton(onClick = {
                                archive = m.file
                                menuFor = null
                            }) { Text("Посмотреть содержимое") }
                            TextButton(onClick = {
                                menuFor = null
                                runOp("Распаковка…", "Распаковано", "Не удалось распаковать") {
                                    unzipArchive(m.file) != null
                                }
                            }) { Text("Распаковать") }
                        }
                        if (!m.isDir) {
                            TextButton(onClick = {
                                menuFor = null
                                openWithSystem(context, m.file)
                            }) { Text("Открыть с помощью…") }
                        }
                        TextButton(onClick = {
                            renameText = m.name
                            renameFor = m.file
                            menuFor = null
                        }) { Text("Переименовать") }
                        TextButton(onClick = {
                            clip = Pair(m.file, false)
                            menuFor = null
                            toast("Скопировано. Открой папку и нажми «Вставить»")
                        }) { Text("Копировать") }
                        TextButton(onClick = {
                            clip = Pair(m.file, true)
                            menuFor = null
                            toast("Выбрано. Открой папку и нажми «Вставить»")
                        }) { Text("Переместить") }
                        TextButton(onClick = {
                            deleteFor = m.file
                            menuFor = null
                        }) { Text("Удалить") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { menuFor = null }) { Text("Отмена") } }
            )
        }

        // Создание
        if (showCreate) {
            AlertDialog(
                onDismissRequest = { showCreate = false },
                title = { Text("Создать") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            label = { Text("Имя") }
                        )
                        Row {
                            TextButton(onClick = { createItem(false) }) { Text("Файл") }
                            TextButton(onClick = { createItem(true) }) { Text("Папка") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } }
            )
        }

        // Переименование
        val rf = renameFor
        if (rf != null) {
            AlertDialog(
                onDismissRequest = { renameFor = null },
                title = { Text("Переименовать") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val n = renameText.trim()
                        val target = File(rf.parentFile, n)
                        if (n.isEmpty() || n.contains('/')) {
                            toast("Некорректное имя")
                        } else if (target.exists()) {
                            toast("Такое имя уже занято")
                        } else {
                            toast(if (rf.renameTo(target)) "Переименовано" else "Не удалось переименовать")
                            refresh++
                        }
                        renameFor = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { renameFor = null }) { Text("Отмена") } }
            )
        }

        // Удаление
        val df = deleteFor
        if (df != null) {
            AlertDialog(
                onDismissRequest = { deleteFor = null },
                title = { Text("Удалить «" + df.name + "»?") },
                text = { Text("Это действие нельзя отменить.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteFor = null
                        runOp(null, "Удалено", "Не удалось удалить") { df.deleteRecursively() }
                    }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Отмена") } }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(
    title: String,
    subtitle: String?,
    isDir: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, Purple), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDir) Purple else PurpleDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDir) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                tint = if (isDir) Color.Black else PurpleLight
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}