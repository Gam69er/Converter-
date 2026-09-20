package com.example.myconverter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var spotifyClientId by remember { mutableStateOf("") }
    var spotifyClientSecret by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableIntStateOf(1) }
    var statusText by remember { mutableStateOf("Ready to convert") }
    var downloadProgress by remember { mutableStateOf("0%") }
    var isWorking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    var menuExpanded by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var customFolderPath by remember { mutableStateOf("/storage/emulated/0/Download") }

    val scope = rememberCoroutineScope()

    // Helper function to check and request All Files Access
    fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
            statusText = "Action required: Grant 'All Files Access' to scan."
            return false
        }
        return true
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Scan Specific Folder", color = Color.White) },
            text = {
                Column {
                    Text("Enter full folder path to scan for missing genres:", color = SpotifyTextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customFolderPath,
                        onValueChange = { customFolderPath = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen, unfocusedBorderColor = Color.DarkGray)
                    )
                }
            },
            containerColor = SpotifyCardBg,
            confirmButton = {
                TextButton(onClick = {
                    showFolderDialog = false
                    if (checkStoragePermission()) {
                        isWorking = true
                        statusText = "Scanning folder..."
                        scope.launch(Dispatchers.IO) {
                            try {
                                val py = Python.getInstance()
                                val module = py.getModule("tagger")
                                val callback = object : ProgressCallback { override fun onProgress(text: String) { downloadProgress = text } }
                                val result = module.callAttr("scan_and_update_library", customFolderPath, false, spotifyClientId, spotifyClientSecret, callback).toString()
                                withContext(Dispatchers.Main) { isWorking = false; statusText = result }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { isWorking = false; statusText = "Scan Failed: ${e.message}" }
                            }
                        }
                    }
                }) { Text("Start Scan", color = SpotifyGreen) }
            },
            dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Safe YT Converter", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "With Spotify Artwork & Synced Lyrics", fontSize = 12.sp, color = SpotifyGreen)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, modifier = Modifier.width(240.dp)) {
                    DropdownMenuItem(
                        text = { Text("Scan Specific Folder") },
                        onClick = {
                            menuExpanded = false
                            if (checkStoragePermission()) showFolderDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Scan ALL Songs on Device") },
                        onClick = {
                            menuExpanded = false
                            if (checkStoragePermission()) {
                                isWorking = true
                                statusText = "Scanning full device..."
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val py = Python.getInstance()
                                        val module = py.getModule("tagger")
                                        val callback = object : ProgressCallback { override fun onProgress(text: String) { downloadProgress = text } }
                                        val result = module.callAttr("scan_and_update_library", "", true, spotifyClientId, spotifyClientSecret, callback).toString()
                                        withContext(Dispatchers.Main) { isWorking = false; statusText = result }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { isWorking = false; statusText = "Full Scan Failed: ${e.message}" }
                                    }
                                }
                            }
                        }
                    )
                }
            }
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
                    FilterChip(selected = selectedFormat == 1, onClick = { selectedFormat = 1 }, label = { Text("Audio + Lyrics") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen, selectedLabelColor = Color.Black))
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

        Button(
            onClick = {
                isWorking = true
                statusText = "Downloading..."
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance()
                        val module = py.getModule("downloader")
                        val callback = object : ProgressCallback { override fun onProgress(percentage: String) { downloadProgress = percentage } }
                        val result = module.callAttr("run_download_and_process", url, selectedFormat, searchQuery, spotifyClientId, spotifyClientSecret, callback).toString()
                        withContext(Dispatchers.Main) {
                            isWorking = false
                            statusText = if (result == "Success") "Complete! Saved to Downloads" else result
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { isWorking = false; statusText = "Failed: ${e.message}" }
                    }
                }
            },
            enabled = !isWorking && url.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("DOWNLOAD", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
