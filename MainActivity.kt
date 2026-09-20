package com.example.myconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ProgressCallback {
    fun onProgress(percentage: String)
}

val SpotifyGreen = Color(0xFF1DB954)
val SpotifyDarkBg = Color(0xFF121212)
val SpotifyCardBg = Color(0xFF181818)
val SpotifyTextMuted = Color(0xFFB3B3B3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = SpotifyGreen, background = SpotifyDarkBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = SpotifyDarkBg) {
                    ConverterScreen()
                }
            }
        }
    }
}

@Composable
fun ConverterScreen() {
    var url by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var spotifyClientId by remember { mutableStateOf("") }
    var spotifyClientSecret by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableIntStateOf(1) } // 1 = MP3, 2 = MP4
    var statusText by remember { mutableStateOf("Ready to convert") }
    var downloadProgress by remember { mutableStateOf("0%") }
    var isDownloading by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Safe YT Converter",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "With Spotify Artwork & Synced Lyrics",
                fontSize = 13.sp,
                color = SpotifyGreen
            )
        }

        // Main Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SpotifyCardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Paste YouTube URL", color = SpotifyTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Song & Artist Name (Optional Override)", color = SpotifyTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Format Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = selectedFormat == 1,
                        onClick = { selectedFormat = 1 },
                        label = { Text("MP3 + Synced Lyrics") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black
                        )
                    )
                    FilterChip(
                        selected = selectedFormat == 2,
                        onClick = { selectedFormat = 2 },
                        label = { Text("MP4 Video") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = Color.Black
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { showSettings = !showSettings }) {
                    Text(if (showSettings) "▲ Hide Spotify API Settings" else "▼ Optional: Spotify API Keys", color = SpotifyTextMuted)
                }

                if (showSettings) {
                    OutlinedTextField(
                        value = spotifyClientId,
                        onValueChange = { spotifyClientId = it },
                        label = { Text("Spotify Client ID", color = SpotifyTextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = spotifyClientSecret,
                        onValueChange = { spotifyClientSecret = it },
                        label = { Text("Spotify Client Secret", color = SpotifyTextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Status & Progress Bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isDownloading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = SpotifyGreen,
                    trackColor = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = statusText,
                color = if (statusText.contains("Error")) Color.Red else Color.White,
                fontSize = 14.sp
            )
            if (isDownloading) {
                Text(text = "Progress: $downloadProgress", color = SpotifyGreen, fontSize = 12.sp)
            }
        }

        // Action Button
        Button(
            onClick = {
                isDownloading = true
                statusText = "Downloading & fetching synced lyrics..."
                scope.launch(Dispatchers.IO) {
                    val py = Python.getInstance()
                    val module = py.getModule("downloader")

                    val callback = object : ProgressCallback {
                        override fun onProgress(percentage: String) {
                            downloadProgress = percentage
                        }
                    }

                    val result = module.callAttr(
                        "run_download_and_process",
                        url,
                        selectedFormat,
                        searchQuery,
                        spotifyClientId,
                        spotifyClientSecret,
                        callback
                    ).toString()

                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        statusText = if (result == "Success") "Complete! Saved to Download/MyConverter" else "Error: $result"
                    }
                }
            },
            enabled = !isDownloading && url.isNotBlank(),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (isDownloading) "PROCESSING..." else "DOWNLOAD & TAG", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
