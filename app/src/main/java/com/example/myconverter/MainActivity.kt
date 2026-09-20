package com.example.myconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.io.PrintWriter
import java.io.StringWriter

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
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(application))
            }
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(primary = SpotifyGreen, background = SpotifyDarkBg)) {
                    Surface(modifier = Modifier.fillMaxSize(), color = SpotifyDarkBg) {
                        ConverterScreen()
                    }
                }
            }
        } catch (e: Throwable) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                            Text("APP CRASHED!", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(sw.toString(), color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
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
    var selectedFormat by remember { mutableIntStateOf(1) }
    var statusText by remember { mutableStateOf("Ready to convert") }
    var downloadProgress by remember { mutableStateOf("0%") }
    var isWorking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Scan Library for Genres", color = Color.White) },
            text = { Text("Choose a folder to scan. Files missing a genre will be automatically tagged via Spotify.", color = SpotifyTextMuted) },
            containerColor = SpotifyCardBg,
            confirmButton = {
                TextButton(onClick = {
                    showScanDialog = false
                    isWorking = true
                    statusText = "Scanning Downloads folder..."
                    scope.launch(Dispatchers.IO) {
                        try {
                            val py = Python.getInstance()
                            val module = py.getModule("tagger")
                            val callback = object : ProgressCallback {
                                override fun onProgress(text: String) { downloadProgress = text }
                            }
                            val result = module.callAttr("scan_and_update_library", "/storage/emulated/0/Download", spotifyClientId, spotifyClientSecret, callback).toString()
                            withContext(Dispatchers.Main) {
                                isWorking = false
                                statusText = result
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isWorking = false
                                statusText = "Scan Failed: ${e.message}"
                            }
                        }
                    }
                }) { Text("Scan Downloads", color = SpotifyGreen) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showScanDialog = false
                    isWorking = true
                    statusText = "Scanning Music folder..."
                    scope.launch(Dispatchers.IO) {
                        try {
                            val py = Python.getInstance()
                            val module = py.getModule("tagger")
                            val callback = object : ProgressCallback {
                                override fun onProgress(text: String) { downloadProgress = text }
                            }
                            val result = module.callAttr("scan_and_update_library", "/storage/emulated/0/Music", spotifyClientId, spotifyClientSecret, callback).toString()
                            withContext(Dispatchers.Main) {
                                isWorking = false
                                statusText = result
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isWorking = false
                                statusText = "Scan Failed: ${e.message}"
                            }
                        }
                    }
                }) { Text("Scan Music", color = SpotifyGreen) }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Safe YT Converter", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = "With Spotify Artwork & Synced Lyrics", fontSize = 13.sp, color = SpotifyGreen)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SpotifyCardBg), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, label = { Text("Paste YouTube URL", color = SpotifyTextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen, unfocusedBorderColor = Color.DarkGray, focusedLabelColor = SpotifyGreen)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Song & Artist Name (Optional)", color = SpotifyTextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen, unfocusedBorderColor = Color.DarkGray, focusedLabelColor = SpotifyGreen)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = selectedFormat == 1, onClick = { selectedFormat = 1 }, label = { Text("MP3 + Lyrics") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen, selectedLabelColor = Color.Black))
                    FilterChip(selected = selectedFormat == 2, onClick = { selectedFormat = 2 }, label = { Text("MP4 Video") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen, selectedLabelColor = Color.Black))
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "▲ Hide API Settings" else "▼ Spotify API Keys", color = SpotifyTextMuted) }
                
                if (showSettings) {
                    OutlinedTextField(value = spotifyClientId, onValueChange = { spotifyClientId = it }, label = { Text("Spotify Client ID", color = SpotifyTextMuted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = spotifyClientSecret, onValueChange = { spotifyClientSecret = it }, label = { Text("Spotify Client Secret", color = SpotifyTextMuted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp), color = SpotifyGreen, trackColor = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(text = statusText, color = if (statusText.contains("ERROR", ignoreCase = true) || statusText.contains("Failed", ignoreCase = true)) Color.Red else Color.White, fontSize = 14.sp)
            if (isWorking) {
                Text(text = "Status: $downloadProgress", color = SpotifyGreen, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { showScanDialog = true },
                enabled = !isWorking,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(52.dp).padding(end = 8.dp)
            ) { Text("BATCH SCAN") }

            Button(
                onClick = {
                    isWorking = true
                    statusText = "Downloading..."
                    scope.launch(Dispatchers.IO) {
                        try {
                            val py = Python.getInstance()
                            val module = py.getModule("downloader")
                            val callback = object : ProgressCallback {
                                override fun onProgress(percentage: String) { downloadProgress = percentage }
                            }
                            val result = module.callAttr("run_download_and_process", url, selectedFormat, searchQuery, spotifyClientId, spotifyClientSecret, callback).toString()
                            withContext(Dispatchers.Main) {
                                isWorking = false
                                statusText = if (result == "Success") "Complete! Saved to Downloads" else result
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isWorking = false
                                statusText = "Failed: ${e.message}"
                            }
                        }
                    }
                },
                enabled = !isWorking && url.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
                modifier = Modifier.weight(1f).height(52.dp).padding(start = 8.dp)
            ) { Text("DOWNLOAD", fontWeight = FontWeight.Bold) }
        }
    }
}
