package com.example.codefiles

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private data class ApkEntryInfo(val name: String, val size: Long, val kind: String)
private data class ApkReport(
    val fileName: String,
    val totalSize: Long,
    val entries: List<ApkEntryInfo>,
    val dexNames: List<String>,
    val classNames: List<String>,
    val methodSummaries: List<String>,
    val packageInfo: String,
    val manifestNote: String
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ApkAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ApkReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            error = null
            report = null
            scope.launch {
                try {
                    report = withContext(Dispatchers.IO) { inspectApk(context, uri) }
                } catch (e: Exception) {
                    error = e.message ?: "Не удалось прочитать APK"
                } finally {
                    busy = false
                }
            }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("APK Analyzer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = barColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Text("  Выбрать APK")
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Purple)
                    Text("  Анализируем архив…", color = PurpleLight)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val data = report
            if (data != null) {
                Card(colors = CardDefaults.cardColors(containerColor = PurpleDark)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(data.fileName, style = MaterialTheme.typography.titleMedium)
                        Text("Размер: ${formatSize(data.totalSize)}")
                        Text("Файлов внутри: ${data.entries.size}")
                        Text("DEX-файлов: ${data.dexNames.size}")
                        Text("Найдено классов (показано до 500): ${data.classNames.size}")
                        Text(data.packageInfo, style = MaterialTheme.typography.bodySmall)
                        Text(data.manifestNote, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (data.dexNames.isNotEmpty()) {
                    Button(
                        onClick = {
                            busy = true
                            busyText = "Дизассемблирование DEX…"
                            scope.launch {
                                try {
                                    val output = withContext(Dispatchers.IO) { disassembleApk(context, data) }
                                    Toast.makeText(context, "Smali-архив создан: ${output.name}", Toast.LENGTH_LONG).show()
                                    openWithSystem(context, output)
                                } catch (e: Exception) {
                                    error = e.message ?: "Ошибка дизассемблирования"
                                } finally {
                                    busy = false
                                    busyText = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text("Дизассемблировать DEX в Smali") }
                    Text(
                        "Smali — читаемое представление байткода. Это не восстановленные исходники Kotlin/Java.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (data.classNames.isNotEmpty()) {
                    Text("Классы DEX", style = MaterialTheme.typography.titleMedium, color = PurpleLight)
                    data.classNames.take(100).forEach { name -> Text(name, fontSize = 12.sp, color = PurpleLight) }
                    if (data.methodSummaries.isNotEmpty()) {
                        Text("Методы (первые 200)", style = MaterialTheme.typography.titleMedium, color = PurpleLight)
                        data.methodSummaries.take(200).forEach { name -> Text(name, fontSize = 11.sp) }
                    }
                }
                Text("Содержимое APK", style = MaterialTheme.typography.titleMedium, color = PurpleLight)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(data.entries, key = { it.name }) { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Android, contentDescription = null, tint = Purple)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatSize(entry.size), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (!busy && error == null) {
                Text(
                    "Выбери APK. Анализатор покажет структуру архива, ресурсы, библиотеки и DEX-классы.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun inspectApk(context: android.content.Context, uri: Uri): ApkReport {
    val temp = File(context.cacheDir, "selected-analysis.apk")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Не удалось открыть выбранный файл" }
        temp.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }
    }

    val entries = ArrayList<ApkEntryInfo>()
    val dexNames = ArrayList<String>()
    val classes = ArrayList<String>()
    val methods = ArrayList<String>()
    var packageInfo = "Информация о пакете недоступна"
    var manifestNote = "AndroidManifest.xml: не найден"
    ZipFile(temp).use { zip ->
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            val name = entry.name
            val kind = when {
                name == "AndroidManifest.xml" -> {
                    manifestNote = "Манифест найден (обычно бинарный AXML)"
                    "Манифест"
                }
                name.startsWith("res/") -> "Ресурс"
                name.startsWith("lib/") -> "Нативная библиотека"
                name.startsWith("assets/") -> "Assets"
                name.matches(Regex("classes(\\d*)\\.dex")) -> {
                    dexNames.add(name)
                    "DEX / байткод"
                }
                else -> "Файл"
            }
            entries.add(ApkEntryInfo(name, entry.size.coerceAtLeast(0), kind))
        }

        dexNames.forEach { dexName ->
            val dexEntry = zip.getEntry(dexName) ?: return@forEach
            val dexTemp = File(context.cacheDir, "inspect-${dexName.replace('/', '_')}")
            zip.getInputStream(dexEntry).use { input ->
                dexTemp.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) }
            }
            FileInputStream(dexTemp).use { input ->
                val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), input)
                for (clazz in dex.classes) {
                    val className = clazz.type.removePrefix("L").removeSuffix(";").replace('/', '.')
                    if (classes.size < 500) classes.add(className)
                    if (methods.size < 500) {
                        for (method in clazz.methods) {
                            if (methods.size >= 500) break
                            methods.add("$className -> ${method.name}")
                        }
                    }
                }
            }
            dexTemp.delete()
        }
    }
    val packageArchive = context.packageManager.getPackageArchiveInfo(temp.absolutePath, android.content.pm.PackageManager.GET_PERMISSIONS)
    if (packageArchive != null) {
        val requested = packageArchive.requestedPermissions?.joinToString(", ").orEmpty()
        packageInfo = "Пакет: ${packageArchive.packageName}\\nВерсия: ${packageArchive.versionName ?: "не указана"}\\nРазрешения: ${requested.ifBlank { "не указаны" }}"
    }
    entries.sortBy { it.name }
    return ApkReport(temp.name, temp.length(), entries, dexNames, classes, methods, packageInfo, manifestNote)
}

private fun disassembleApk(context: android.content.Context, report: ApkReport): File {
    val apk = File(context.cacheDir, "selected-analysis.apk")
    require(apk.exists()) { "Сначала выбери APK ещё раз" }
    val outDir = File(context.cacheDir, "smali-output")
    outDir.deleteRecursively()
    outDir.mkdirs()

    ZipFile(apk).use { zip ->
        report.dexNames.forEachIndexed { index, name ->
            val entry = zip.getEntry(name) ?: return@forEachIndexed
            val dexFile = File(context.cacheDir, "disassemble-$index.dex")
            zip.getInputStream(entry).use { input ->
                dexFile.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) }
            }
            FileInputStream(dexFile).use { input ->
                val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), input)
                val target = if (index == 0) outDir else File(outDir, "dex_$index").apply { mkdirs() }
                Baksmali.disassembleDexFile(dex, target, 1, BaksmaliOptions())
            }
            dexFile.delete()
        }
    }

    val archive = File(context.cacheDir, "PipisEdit-smali.zip")
    ZipOutputStream(archive.outputStream().buffered()).use { output ->
        outDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(outDir).invariantSeparatorsPath
            output.putNextEntry(ZipEntry(relative))
            file.inputStream().use { it.copyTo(output, 64 * 1024) }
            output.closeEntry()
        }
    }
    outDir.deleteRecursively()
    return archive
}
