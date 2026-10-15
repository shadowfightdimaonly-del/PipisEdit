package com.example.codefiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class Decoded(val bmp: Bitmap, val w: Int, val h: Int)

private fun decodeImage(file: File): Decoded? =
    try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (o.outWidth / sample > 4096 || o.outHeight / sample > 4096) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val b = BitmapFactory.decodeFile(file.absolutePath, o2)
            if (b == null) null else Decoded(b, o.outWidth, o.outHeight)
        }
    } catch (e: Throwable) {
        null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewer(file: File, onBack: () -> Unit) {
    var decoded by remember(file) { mutableStateOf<Decoded?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file) {
        val d = withContext(Dispatchers.IO) { decodeImage(file) }
        if (d == null) failed = true else decoded = d
    }

    BackHandler { onBack() }

    val d0 = decoded
    val title = if (d0 != null) file.name + "  " + d0.w + "×" + d0.h else file.name

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = barColors()
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput("transform") {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 20f)
                        offset = offset + pan
                    }
                }
                .pointerInput("tap") {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            val d = decoded
            if (failed) {
                Text("Не удалось открыть изображение")
            } else if (d == null) {
                CircularProgressIndicator(color = Purple)
            } else {
                Image(
                    bitmap = d.bmp.asImageBitmap(),
                    contentDescription = null,
                    filterQuality = FilterQuality.None,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
        }
    }
}