package com.example.codefiles

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    private val legacyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasAccess = checkAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

fun unpackInBackground(context: Context, archive: File, onDone: () -> Unit) {
    Toast.makeText(context, "Распаковка…", Toast.LENGTH_SHORT).show()
    val handler = Handler(Looper.getMainLooper())
    Thread {
        val result = unzipArchive(archive)
        handler.post {
            if (result == null) {
                Toast.makeText(context, "Не удалось распаковать", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Готово: папка «" + result.name + "»", Toast.LENGTH_LONG).show()
                onDone()
            }
        }
    }.start()
}

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
    val root = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(root) }
    var opened by remember { mutableStateOf<File?>(null) }
    var archive by remember { mutableStateOf<File?>(null) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var refresh by remember { mutableStateOf(0) }

    val file = opened
    val arc = archive
    if (file != null) {
        BackHandler { opened = null }
        FileViewer(file = file, onBack = { opened = null })
    } else if (arc != null) {
        ArchiveViewer(archive = arc, onBack = { archive = null })
    } else {
        BackHandler(enabled = current != root) {
            current = current.parentFile ?: root
        }

        val entries = remember(current, refresh) {
            (current.listFiles()?.toList() ?: emptyList())
                .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            current.absolutePath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        if (current != root) {
                            IconButton(onClick = { current = current.parentFile ?: root }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = PurpleLight,
                        navigationIconContentColor = Purple
                    )
                )
            }
        ) { padding ->
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Папка пуста")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(entries, key = { it.absolutePath }) { item ->
                        FileRow(
                            file = item,
                            onClick = {
                                val ext = item.extension.lowercase()
                                if (item.isDirectory) {
                                    current = item
                                } else if (ext in ARCHIVE_EXT) {
                                    openWithSystem(context, item)
                                } else if (ext in TEXT_EXT || ext.isEmpty()) {
                                    opened = item
                                } else {
                                    openWithSystem(context, item)
                                }
                            },
                            onLongClick = {
                                if (item.isFile && item.extension.lowercase() in ARCHIVE_EXT) {
                                    menuFor = item
                                }
                            }
                        )
                    }
                }
            }
        }

        val target = menuFor
        if (target != null) {
            AlertDialog(
                onDismissRequest = { menuFor = null },
                title = { Text(target.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        TextButton(onClick = {
                            archive = target
                            menuFor = null
                        }) { Text("Посмотреть содержимое") }
                        TextButton(onClick = {
                            menuFor = null
                            unpackInBackground(context, target) { refresh++ }
                        }) { Text("Распаковать") }
                        TextButton(onClick = {
                            menuFor = null
                            openWithSystem(context, target)
                        }) { Text("Открыть с помощью…") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { menuFor = null }) { Text("Отмена") } }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(file: File, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
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
                .background(if (file.isDirectory) Purple else PurpleDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                tint = if (file.isDirectory) Color.Black else PurpleLight
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (file.isFile) {
                Text(
                    formatSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "${bytes / (1024 * 1024)} МБ"
}