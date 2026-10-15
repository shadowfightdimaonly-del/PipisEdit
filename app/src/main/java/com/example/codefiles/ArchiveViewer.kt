package com.example.codefiles

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.zip.ZipFile

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
    val entries = remember(archive) { readZipEntries(archive) }
    var prefix by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<File?>(null) }

    val shown = viewing
    if (shown != null) {
        BackHandler { viewing = null }
        FileViewer(file = shown, onBack = { viewing = null })
    } else {
        fun goUp() {
            if (prefix.isEmpty()) onBack() else prefix = parentPrefix(prefix)
        }

        BackHandler { goUp() }

        val nodes = remember(entries, prefix) {
            if (entries == null) emptyList<ZipNode>() else children(entries, prefix)
        }

        Scaffold(
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
                    }
                )
            }
        ) { padding ->
            if (entries == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Не удалось открыть архив")
                }
            } else if (nodes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Пусто")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(nodes) { node ->
                        ZipRow(node) {
                            if (node.isDir) {
                                prefix = prefix + node.name + "/"
                            } else {
                                val ext = node.name.substringAfterLast('.', "").lowercase()
                                if (!(ext in TEXT_EXT || ext.isEmpty())) {
                                    Toast.makeText(
                                        context,
                                        "Этот тип файла внутри архива не открыть. Распакуй архив.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else if (node.size > 1024 * 1024) {
                                    Toast.makeText(context, "Файл больше 1 МБ", Toast.LENGTH_LONG).show()
                                } else {
                                    val f = extractEntry(context, archive, prefix + node.name)
                                    if (f == null) {
                                        Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewing = f
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZipRow(node: ZipNode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (node.isDir) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = if (node.isDir) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!node.isDir) {
                Text(
                    formatSize(node.size.coerceAtLeast(0L)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}