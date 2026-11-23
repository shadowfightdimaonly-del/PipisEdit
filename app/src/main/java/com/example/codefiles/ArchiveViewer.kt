package com.example.codefiles

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private class ZipNode(val name: String, val isDir: Boolean, val size: Long)

private fun readZipEntries(archive: File): List<Pair<String, Long>>? =
    try {
        ZipFile(archive).use { zip ->
            zip.entries().toList().map { it.name to it.size }
        }
    } catch (e: Exception) {
        null
    }

private fun children(entries: List<Pair<String, Long>>, prefix: String): List<ZipNode> {
    val dirs = mutableSetOf<String>()
    val files = mutableListOf<ZipNode>()
    for ((path, size) in entries) {
        if (!path.startsWith(prefix) || path == prefix) continue
        val rest = path.removePrefix(prefix)
        val slash = rest.indexOf('/')
        if (slash >= 0) {
            dirs.add(rest.substring(0, slash))
        } else {
            files.add(ZipNode(rest, false, size))
        }
    }
    val dirNodes = dirs.sortedBy { it.lowercase() }.map { ZipNode(it, true, 0L) }
    return dirNodes + files.sortedBy { it.name.lowercase() }
}

private fun parentPrefix(prefix: String): String {
    val trimmed = prefix.trimEnd('/')
    val i = trimmed.lastIndexOf('/')
    return if (i < 0) "" else trimmed.substring(0, i + 1)
}

private fun replaceZipEntry(archive: File, path: String, updatedText: String): Boolean = try {
    val temp = File(archive.parentFile, archive.name + ".pipisedit-tmp")
    var found = false
    ZipFile(archive).use { source ->
        ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { output ->
            val entries = source.entries()
            while (entries.hasMoreElements()) {
                val old = entries.nextElement()
                val replacement = ZipEntry(old.name).apply {
                    if (old.time >= 0) time = old.time
                    old.comment?.let { comment = it }
                }
                output.putNextEntry(replacement)
                if (old.name == path) {
                    found = true
                    output.write(updatedText.toByteArray(StandardCharsets.UTF_8))
                } else if (!old.isDirectory) {
                    source.getInputStream(old).use { it.copyTo(output, 32 * 1024) }
                }
                output.closeEntry()
            }
        }
    }
    if (!found) {
        temp.delete()
        false
    } else {
        try {
            android.system.Os.rename(temp.absolutePath, archive.absolutePath)
            true
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }
} catch (e: Exception) {
    File(archive.parentFile, archive.name + ".pipisedit-tmp").delete()
    false
}

private fun extractEntry(context: Context, archive: File, path: String): File? =
    try {
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(path)
            if (entry == null) {
                null
            } else {
                val dir = File(context.cacheDir, "zipview")
                dir.mkdirs()
                val out = File(dir, path.substringAfterLast('/'))
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out
            }
        }
    } catch (e: Exception) {
        null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewer(archive: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember(archive) { readZipEntries(archive) }
    var prefix by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<File?>(null) }
    var prefixForViewing by remember { mutableStateOf("") }

    val shown = viewing
    if (shown != null) {
        if (shown.extension.lowercase() in IMAGE_EXT) {
            ImageViewer(file = shown, onBack = { viewing = null })
        } else {
            FileViewer(
                file = shown,
                onBack = { viewing = null },
                editable = shown.extension.lowercase() in TEXT_EXT || shown.extension.isEmpty(),
                onSave = { updatedText -> replaceZipEntry(archive, prefixForViewing, updatedText) }
            )
        }
    } else {
        fun goUp() {
            if (prefix.isEmpty()) onBack() else prefix = parentPrefix(prefix)
        }

        BackHandler { goUp() }

        val nodes = remember(entries, prefix) {
            if (entries == null) emptyList<ZipNode>() else children(entries, prefix)
        }

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            archive.name + "/" + prefix,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { goUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = barColors()
                )
            }
        ) { padding ->
            if (entries == null || nodes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (entries == null) "Не удалось открыть архив" else "Пусто")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(nodes) { node ->
                        ItemRow(
                            title = node.name,
                            subtitle = if (node.isDir) null else formatSize(node.size.coerceAtLeast(0L)),
                            isDir = node.isDir,
                            onClick = {
                                if (node.isDir) {
                                    prefix = prefix + node.name + "/"
                                } else {
                                    val ext = node.name.substringAfterLast('.', "").lowercase()
                                    if (!(ext in TEXT_EXT || ext in IMAGE_EXT || ext.isEmpty())) {
                                        Toast.makeText(
                                            context,
                                            "Этот тип файла внутри архива не открыть. Распакуй архив.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else if (node.size > 20L * 1024 * 1024) {
                                        Toast.makeText(context, "Файл больше 20 МБ", Toast.LENGTH_LONG).show()
                                    } else {
                                        val path = prefix + node.name
                                        scope.launch {
                                            val f = withContext(Dispatchers.IO) {
                                                extractEntry(context, archive, path)
                                            }
                                            if (f == null) {
                                                Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_LONG).show()
                                            } else {
                                                prefixForViewing = path
                                                viewing = f
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}