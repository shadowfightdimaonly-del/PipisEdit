package com.example.codefiles

import android.content.Context
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

data class TrashRecord(val originalPath: String, val trashedPath: String) {
    val originalName: String get() = File(originalPath).name
    val trashedFile: File get() = File(trashedPath)
}

object TrashStore {
    private const val PREFS = "pipis_edit_trash"
    private const val KEY = "records"

    fun records(context: Context): List<TrashRecord> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val split = line.split('\t', limit = 2)
                if (split.size == 2) TrashRecord(split[0], split[1]) else null
            }
            .filter { it.trashedFile.exists() }
            .toList()

    private fun save(context: Context, items: List<TrashRecord>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, items.joinToString("\n") { it.originalPath + "\t" + it.trashedPath })
            .apply()
    }

    fun moveToTrash(context: Context, source: File): Boolean {
        if (!source.exists() || source.parentFile == null) return false
        return try {
            val folder = File(source.parentFile, ".PipisEditTrash")
            if (!folder.exists() && !folder.mkdirs()) return false
            val target = uniqueTarget(folder, source.name + ".deleted")
            val moved = if (source.renameTo(target)) true else {
                val copied = if (source.isDirectory) source.copyRecursively(target, overwrite = false)
                else source.copyTo(target, overwrite = false).exists()
                if (copied) source.deleteRecursively() else false
            }
            if (!moved) {
                target.deleteRecursively()
                return false
            }
            val updated = records(context) + TrashRecord(source.absolutePath, target.absolutePath)
            save(context, updated)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restore(context: Context, record: TrashRecord): Boolean {
        val stored = record.trashedFile
        if (!stored.exists()) return false
        return try {
            val original = File(record.originalPath)
            val parent = original.parentFile ?: return false
            if (!parent.exists() && !parent.mkdirs()) return false
            val target = if (original.exists()) uniqueTarget(parent, original.name) else original
            val ok = if (stored.renameTo(target)) true else {
                val copied = if (stored.isDirectory) stored.copyRecursively(target, overwrite = false)
                else stored.copyTo(target, overwrite = false).exists()
                if (copied) stored.deleteRecursively() else false
            }
            if (ok) save(context, records(context).filterNot { it.trashedPath == record.trashedPath })
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun deletePermanently(context: Context, record: TrashRecord): Boolean {
        val ok = record.trashedFile.deleteRecursively()
        if (ok) save(context, records(context).filterNot { it.trashedPath == record.trashedPath })
        return ok
    }

    fun clear(context: Context): Boolean {
        val all = records(context)
        var success = true
        all.forEach { if (!it.trashedFile.deleteRecursively()) success = false }
        save(context, records(context).filter { it.trashedFile.exists() })
        return success
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val items = remember(refresh) { TrashStore.records(context) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Корзина") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) {
                        Text("Очистить")
                    }
                },
                colors = barColors()
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { Text("Корзина пуста", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(items, key = { it.trashedPath }) { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.originalName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                record.originalPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            if (TrashStore.restore(context, record)) {
                                refresh++
                                onChanged()
                            }
                        }) { Icon(Icons.Filled.Restore, contentDescription = "Восстановить") }
                        IconButton(onClick = {
                            if (TrashStore.deletePermanently(context, record)) refresh++
                        }) { Icon(Icons.Filled.DeleteForever, contentDescription = "Удалить навсегда") }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить корзину?") },
            text = { Text("Все элементы будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    TrashStore.clear(context)
                    confirmClear = false
                    refresh++
                    onChanged()
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } }
        )
    }
}
