package com.example.codefiles

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    private val legacyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasAccess = checkAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
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
    val root = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(root) }
    var opened by remember { mutableStateOf<File?>(null) }

    val file = opened
    if (file != null) {
        BackHandler { opened = null }
        FileViewer(file = file, onBack = { opened = null })
    } else {
        BackHandler(enabled = current != root) {
            current = current.parentFile ?: root
        }

        val entries = remember(current) {
            (current.listFiles()?.toList() ?: emptyList())
                .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        }

        Scaffold(
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
                    }
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
                        FileRow(item) {
                            if (item.isDirectory) current = item else opened = item
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileRow(file: File, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
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
