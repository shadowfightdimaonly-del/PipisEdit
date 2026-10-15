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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateListOf
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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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
val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

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
) {
    val sizeText: String? by lazy(LazyThreadSafetyMode.NONE) {
        if (isDir) null else formatSize(size)
    }
}

class PackJob(val items: List<File>, val ext: String, val errors: List<String>)

object Bookmarks {
    val list = mutableStateListOf<String>()

    fun load(context: Context) {
        val s = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("bookmarks", "") ?: ""
        list.clear()
        list.addAll(s.split("\n").filter { it.isNotBlank() })
    }

    fun save(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("bookmarks", list.joinToString("\n"))
            .apply()
    }
}

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
        Bookmarks.load(this)
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

/**
 * Упаковка набора элементов:
 *  - если выделена ровно ОДНА папка → её содержимое идёт в корень архива (mcpack-стиль);
 *  - иначе → каждая папка становится подпапкой в корне архива (mcaddon-стиль),
 *    а отдельные файлы кладутся в корень архива.
 */
fun zipItems(items: List<File>, out: File): Boolean = try {
    ZipOutputStream(out.outputStream().buffered()).use { zos ->
        val flatten = items.size == 1 && items[0].isDirectory

        if (flatten) {
            val src = items[0]
            val base = src.absolutePath
            src.walkTopDown().forEach { f ->
                if (f == src) return@forEach
                if (f.name == ".DS_Store" || f.name == "Thumbs.db") return@forEach
                val rel = f.absolutePath.removePrefix(base).trimStart('/')
                if (f.isDirectory) {
                    zos.putNextEntry(ZipEntry("$rel/"))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        } else {
            for (item in items) {
                if (item.isDirectory) {
                    val base = item.absolutePath
                    val prefix = item.name + "/"
                    item.walkTopDown().forEach { f ->
                        if (f.name == ".DS_Store" || f.name == "Thumbs.db") return@forEach
                        val rel = if (f == item) prefix
                        else prefix + f.absolutePath.removePrefix(base).trimStart('/')
                        if (f.isDirectory) {
                            zos.putNextEntry(ZipEntry(if (rel.endsWith("/")) rel else "$rel/"))
                            zos.closeEntry()
                        } else {
                            zos.putNextEntry(ZipEntry(rel))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                } else {
                    if (item.name == ".DS_Store" || item.name == "Thumbs.db") continue
                    zos.putNextEntry(ZipEntry(item.name))
                    item.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
    true
} catch (e: Throwable) {
    out.delete()
    false
}

/** Имя архива: если текущая папка — корень внешнего хранилища, то addon.<ext>, иначе <имя_папки>.<ext>. */
fun archiveBaseName(dir: File, ext: String): String {
    val rootPath = Environment.getExternalStorageDirectory().absolutePath
    val name = if (dir.absolutePath == rootPath || dir.name.isBlank()) "addon" else dir.name
    return "$name.$ext"
}

fun scanJsonErrorsMulti(folders: List<File>): List<String> {
    val out = ArrayList<String>()
    for (dir in folders) {
        val base = dir.absolutePath
        for (f in dir.walkTopDown()) {
            if (!f.isFile) continue
            val ext = f.extension.lowercase()
            if (ext != "json" && ext != "mcmeta") continue
            if (f.length() > 3L * 1024 * 1024) continue
            val err = try {
                checkJson(f.readText())
            } catch (e: Throwable) {
                null
            }
            if (err != null) {
                val rel = f.absolutePath.removePrefix(base).trimStart('/')
                out.add("${dir.name}/$rel — $err")
                if (out.size >= 50) return out
            }
        }
    }
    return out
}

fun loadEntries(dir: File): List<Entry> {
    val files = dir.listFiles() ?: return emptyList()
    val out = ArrayList<Entry>(files.size)
    for (f in files) {
        out.add(Entry(f, f.name, f.isDirectory, if (f.isDirectory) 0L else f.length()))
    }
    out.sortWith(
        compareByDescending<Entry> { it.isDir }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    )
    return out
}

suspend fun searchEntries(base: File, query: String): List<Entry> {
    val q = query.lowercase()
    val out = ArrayList<Entry>()
    val stack = ArrayDeque<Pair<File, Int>>()
    stack.add(base to 0)
    var visited = 0
    val basePath = base.absolutePath
    while (stack.isNotEmpty() && out.size < 300 && visited < 30000) {
        currentCoroutineContext().ensureActive()
        val (dir, depth) = stack.removeLast()
        visited++
        val kids = dir.listFiles() ?: continue
        for (f in kids) {
            val isDir = f.isDirectory
            if (f.name.lowercase().contains(q)) {
                val rel = (f.parentFile?.absolutePath ?: "").removePrefix(basePath).ifEmpty { "/" }
                out.add(Entry(f, f.name, isDir, if (isDir) 0L else f.length(), rel))
            }
            if (isDir && depth < 8) stack.add(f to (depth + 1))
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowser() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(root) }
    var opened by remember { mutableStateOf<File?>(null) }
    var image by remember { mutableStateOf<File?>(null) }
    var archive by remember { mutableStateOf<File?>(null) }
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
    var showBookmarks by remember { mutableStateOf(false) }
    var packJob by remember { mutableStateOf<PackJob?>(null) }
    var showPackDialog by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    // === Выделение ===
    val selected = remember { mutableStateListOf<String>() }
    val selectionMode = selected.isNotEmpty()

    fun enterSelection(path: String) {
        searchOn = false
        query = ""
        if (path !in selected) selected.add(path)
    }

    fun toggleSelection(path: String) {
        if (path in selected) selected.remove(path) else selected.add(path)
    }

    fun exitSelection() {
        selected.clear()
    }

    fun selectedFiles(): List<File> = selected.map { File(it) }

    LaunchedEffect(current, refresh) {
    val list = withContext(Dispatchers.IO) { loadEntries(current) }
    entries = list
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

    fun doPack(items: List<File>, ext: String) {
        val dirAtCall = current
        runOp("Упаковка…", "Архив создан", "Не удалось упаковать") {
            val target = uniqueTarget(dirAtCall, archiveBaseName(dirAtCall, ext))
            zipItems(items, target)
        }
    }

    fun startPack(items: List<File>, ext: String) {
        if (ext == "mcaddon" || ext == "mcpack") {
            toast("Проверка JSON…")
            scope.launch {
                val folders = items.filter { it.isDirectory }
                val errors = withContext(Dispatchers.IO) { scanJsonErrorsMulti(folders) }
                if (errors.isEmpty()) doPack(items, ext) else packJob = PackJob(items, ext, errors)
            }
        } else {
            doPack(items, ext)
        }
    }

    fun validateAndPack(items: List<File>, ext: String): Boolean {
        val allDirs = items.all { it.isDirectory }
        when (ext) {
            "mcaddon" -> {
                if (items.size < 2) {
                    toast("Для .mcaddon нужно 2 и более папок")
                    return false
                }
                if (!allDirs) {
                    toast("В .mcaddon можно упаковывать только папки")
                    return false
                }
            }
            "mcpack" -> {
                if (items.size != 1 || !allDirs) {
                    toast("Для .mcpack нужна ровно 1 папка")
                    return false
                }
            }
        }
        return true
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
    val img = image
    val arc = archive
    if (file != null) {
        FileViewer(file = file, onBack = {
            opened = null
            refresh++
        })
    } else if (img != null) {
        ImageViewer(file = img, onBack = { image = null })
    } else if (arc != null) {
        ArchiveViewer(archive = arc, onBack = { archive = null })
    } else {
        // Back: сначала выход из выделения, потом из поиска, потом наверх
        BackHandler(enabled = selectionMode) { exitSelection() }
        BackHandler(enabled = !selectionMode && searchOn) {
            searchOn = false
            query = ""
        }
        BackHandler(enabled = !selectionMode && !searchOn && current != root) {
            current = current.parentFile ?: root
        }

        val shown = if (searchOn && query.isNotBlank()) results else entries

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        when {
                            selectionMode -> {
                                Text(
                                    "Выбрано: ${selected.size}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            searchOn -> {
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
                            }
                            else -> {
                                Text(
                                    current.absolutePath,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (selectionMode) {
                            IconButton(onClick = { exitSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Выйти из выделения")
                            }
                        } else if (current != root) {
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
                        if (selectionMode) {
                            IconButton(onClick = { showPackDialog = true }) {
                                Icon(Icons.Filled.Check, contentDescription = "Запаковать")
                            }
                            Box {
                                IconButton(onClick = { overflowOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                                }
                                DropdownMenu(
                                    expanded = overflowOpen,
                                    onDismissRequest = { overflowOpen = false }
                                ) {
                                    val files = selectedFiles()
                                    val single = files.size == 1
                                    val singleFile = if (single) files[0] else null
                                    val singleIsArchive = singleFile != null &&
                                        !singleFile.isDirectory &&
                                        singleFile.extension.lowercase() in ARCHIVE_EXT

                                    DropdownMenuItem(
                                        text = { Text("Запаковать…") },
                                        onClick = {
                                            overflowOpen = false
                                            showPackDialog = true
                                        }
                                    )
                                    if (singleFile != null && singleIsArchive) {
                                        DropdownMenuItem(
                                            text = { Text("Посмотреть содержимое") },
                                            onClick = {
                                                overflowOpen = false
                                                val f = singleFile
                                                exitSelection()
                                                archive = f
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Распаковать") },
                                            onClick = {
                                                overflowOpen = false
                                                val f = singleFile
                                                exitSelection()
                                                runOp("Распаковка…", "Распаковано", "Не удалось распаковать") {
                                                    unzipArchive(f) != null
                                                }
                                            }
                                        )
                                    }
                                    if (singleFile != null && !singleFile.isDirectory) {
                                        DropdownMenuItem(
                                            text = { Text("Открыть с помощью…") },
                                            onClick = {
                                                overflowOpen = false
                                                openWithSystem(context, singleFile)
                                            }
                                        )
                                    }
                                    if (single) {
                                        DropdownMenuItem(
                                            text = { Text("Переименовать") },
                                            onClick = {
                                                overflowOpen = false
                                                val f = files[0]
                                                exitSelection()
                                                renameText = f.name
                                                renameFor = f
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Копировать") },
                                        onClick = {
                                            overflowOpen = false
                                            val f = files.first()
                                            clip = Pair(f, false)
                                            exitSelection()
                                            toast("Скопировано. Открой папку и нажми «Вставить»")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Переместить") },
                                        onClick = {
                                            overflowOpen = false
                                            val f = files.first()
                                            clip = Pair(f, true)
                                            exitSelection()
                                            toast("Выбрано. Открой папку и нажми «Вставить»")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить") },
                                        onClick = {
                                            overflowOpen = false
                                            if (single) {
                                                deleteFor = files[0]
                                                exitSelection()
                                            } else {
                                                val targets = files
                                                exitSelection()
                                                runOp("Удаление…", "Удалено", "Не удалось удалить") {
                                                    var ok = true
                                                    for (t in targets) if (!t.deleteRecursively()) ok = false
                                                    ok
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            val c = clip
                            if (c != null) {
                                TextButton(onClick = {
                                    runOp("Вставка…", "Готово", "Не удалось вставить") {
                                        pasteInto(c.first, current, c.second)
                                    }
                                    if (c.second) clip = null
                                }) { Text("Вставить", color = Purple) }
                            }
                            IconButton(onClick = { showBookmarks = true }) {
                                Icon(Icons.Filled.Bookmark, contentDescription = "Закладки")
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
                        }
                    },
                    colors = barColors()
                )
            },
            floatingActionButton = {
                if (!searchOn && !selectionMode) {
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
                    items(
                        items = shown,
                        key = { it.file.absolutePath },
                        contentType = { if (it.isDir) 0 else 1 }
                    ) { e ->
                        val path = e.file.absolutePath
                        ItemRow(
                            title = e.name,
                            subtitle = e.sub ?: e.sizeText,
                            isDir = e.isDir,
                            isSelected = path in selected,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(path)
                                } else {
                                    val ext = e.file.extension.lowercase()
                                    when {
                                        e.isDir -> {
                                            current = e.file
                                            searchOn = false
                                            query = ""
                                        }
                                        ext in ARCHIVE_EXT -> openWithSystem(context, e.file)
                                        ext in IMAGE_EXT -> image = e.file
                                        ext in TEXT_EXT || ext.isEmpty() -> opened = e.file
                                        else -> openWithSystem(context, e.file)
                                    }
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) enterSelection(path)
                                else toggleSelection(path)
                            }
                        )
                    }
                }
            }
        }

        // === Диалог упаковки ===
        if (showPackDialog) {
            val items = selectedFiles()
            AlertDialog(
                onDismissRequest = { showPackDialog = false },
                title = { Text("Запаковать ${items.size} эл.") },
                text = {
                    Column {
                        Text(
                            "Содержимое выбранных папок будет упаковано. JSON-файлы проверяются.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextButton(onClick = {
                            if (validateAndPack(items, "mcaddon")) {
                                showPackDialog = false
                                startPack(items, "mcaddon")
                            }
                        }) { Text(".mcaddon — 2+ папки") }
                        TextButton(onClick = {
                            if (validateAndPack(items, "mcpack")) {
                                showPackDialog = false
                                startPack(items, "mcpack")
                            }
                        }) { Text(".mcpack — ровно 1 папка") }
                        TextButton(onClick = {
                            if (validateAndPack(items, "zip")) {
                                showPackDialog = false
                                startPack(items, "zip")
                            }
                        }) { Text(".zip — что угодно") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showPackDialog = false }) { Text("Отмена") } }
            )
        }

        // === Ошибки JSON перед упаковкой ===
        val pj = packJob
        if (pj != null) {
            AlertDialog(
                onDismissRequest = { packJob = null },
                title = { Text("Ошибки в JSON: ${pj.errors.size}") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        for (e in pj.errors.take(10)) {
                            Text(
                                e,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        if (pj.errors.size > 10) {
                            Text("…и ещё ${pj.errors.size - 10}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val job = pj
                        packJob = null
                        doPack(job.items, job.ext)
                    }) { Text("Всё равно запаковать") }
                },
                dismissButton = { TextButton(onClick = { packJob = null }) { Text("Отмена") } }
            )
        }

        // === Закладки ===
        if (showBookmarks) {
            AlertDialog(
                onDismissRequest = { showBookmarks = false },
                title = { Text("Закладки") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        val here = current.absolutePath
                        if (here in Bookmarks.list) {
                            TextButton(onClick = {
                                Bookmarks.list.remove(here)
                                Bookmarks.save(context)
                            }) { Text("Убрать эту папку из закладок") }
                        } else {
                            TextButton(onClick = {
                                Bookmarks.list.add(here)
                                Bookmarks.save(context)
                            }) { Text("Добавить эту папку в закладки") }
                        }
                        if (Bookmarks.list.isEmpty()) {
                            Text(
                                "Закладок пока нет",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        for (p in Bookmarks.list.toList()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        val f = File(p)
                                        if (f.isDirectory) {
                                            current = f
                                            searchOn = false
                                            query = ""
                                            showBookmarks = false
                                        } else {
                                            toast("Папки больше нет")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(File(p).name.ifEmpty { p }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            p,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    Bookmarks.list.remove(p)
                                    Bookmarks.save(context)
                                }) { Icon(Icons.Filled.Close, contentDescription = "Удалить закладку") }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showBookmarks = false }) { Text("Закрыть") } }
            )
        }

        // === Создание ===
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

        // === Переименование ===
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

        // === Удаление ===
        val df = deleteFor
        if (df != null) {
            AlertDialog(
                onDismissRequest = { deleteFor = null },
                title = { Text("Удалить «${df.name}»?") },
                text = { Text("Это действие нельзя отменить.") },
                confirmButton = {
                    TextButton(onClick = {
                        val f = df
                        deleteFor = null
                        runOp(null, "Удалено", "Не удалось удалить") { f.deleteRecursively() }
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
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (isSelected) PurpleLight else Purple
    val bgColor = if (isSelected) Color(0xFF2A1740) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor), shape)
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