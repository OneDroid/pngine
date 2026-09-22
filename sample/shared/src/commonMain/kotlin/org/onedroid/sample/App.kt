package org.onedroid.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.onedroid.sample.png.DemoOptions
import org.onedroid.sample.png.PngEncoder
import org.onedroid.sample.png.TestImage
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.TimeSource

private val PALETTE_SIZES = listOf(16, 32, 64, 128, 256)

private data class EncodeResult(
    val image: ImageBitmap,
    val png8Bytes: Int,
    val baselineBytes: Int,
    val took: Duration,
) {
    /** How much smaller the PNG-8 output is than the platform's 32-bit PNG. */
    val savedPercent: Int =
        (100.0 * (baselineBytes - png8Bytes) / baselineBytes).roundToInt()
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .safeContentPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pngine", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "PNG-8 quantization, pure Kotlin. Running on ${getPlatform().name}.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (PngEncoder.isSupported) {
                    EncoderDemo()
                } else {
                    UnsupportedCard(PngEncoder.unsupportedReason.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun EncoderDemo() {
    val scope = rememberCoroutineScope()
    val pixels = remember { TestImage.argbPixels() }

    var maxColors by remember { mutableStateOf(64) }
    var dithering by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EncodeResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Palette size", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE_SIZES.forEach { size ->
            FilterChip(
                selected = maxColors == size,
                onClick = { maxColors = size },
                label = { Text("$size") },
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Floyd–Steinberg dithering")
        Switch(checked = dithering, onCheckedChange = { dithering = it })
    }

    Button(
        enabled = !running,
        onClick = {
            scope.launch {
                running = true
                error = null
                try {
                    // Encoding is CPU-bound; keep it off the UI thread.
                    val encoded = withContext(Dispatchers.Default) {
                        val mark = TimeSource.Monotonic.markNow()
                        val png8 = PngEncoder.encodePng8(
                            pixels = pixels,
                            width = TestImage.WIDTH,
                            height = TestImage.HEIGHT,
                            options = DemoOptions(maxColors, dithering),
                        )
                        val took = mark.elapsedNow()
                        val baseline = PngEncoder.encodeBaselinePng(
                            pixels = pixels,
                            width = TestImage.WIDTH,
                            height = TestImage.HEIGHT,
                        )
                        Triple(png8, baseline, took)
                    }
                    val (png8, baseline, took) = encoded
                    result = EncodeResult(
                        image = png8.decodeToImageBitmap(),
                        png8Bytes = png8.size,
                        baselineBytes = baseline.size,
                        took = took,
                    )
                } catch (e: Exception) {
                    error = e.message ?: e.toString()
                } finally {
                    running = false
                }
            }
        },
    ) {
        Text(if (running) "Encoding…" else "Encode with Pngine")
    }

    if (running) {
        CircularProgressIndicator()
    }

    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }

    result?.let { r ->
        Image(
            bitmap = r.image,
            contentDescription = "PNG-8 encoded test image",
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Stat("Bitmap.compress(PNG)", formatBytes(r.baselineBytes))
                Stat("Pngine PNG-8", formatBytes(r.png8Bytes))
                Stat("Saved", "${r.savedPercent}%")
                Stat("Encode time", "${r.took.inWholeMilliseconds} ms")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun UnsupportedCard(reason: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Android only", style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Run the androidApp module to see the encoder work.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatBytes(bytes: Int): String {
    val kb = bytes / 1024.0
    val rounded = (kb * 10).roundToInt() / 10.0
    return "$rounded KB"
}
