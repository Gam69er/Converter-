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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

val SpotifyGreen = Color(0xFF1DB954)
val SpotifyDarkBg = Color(0xFF121212)
val SpotifyCardBg = Color(0xFF181818)
val SpotifyTextMuted = Color(0xFFB3B3B3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
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
    var selectedFormat by remember { mutableIntStateOf(1) } // 1 = Audio, 2 = Video
    var statusText by remember { mutableStateOf("Ready to convert") }
    var isWorking by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Safe YT Converter", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = "Connected to Local Termux Engine", fontSize = 13.sp, color = SpotifyGreen)
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                isWorking = true
                statusText = "Sending request to Termux server..."
                scope.launch(Dispatchers.IO) {
                    val result = sendDownloadRequest(url, selectedFormat, searchQuery, spotifyClientId, spotifyClientSecret)
                    withContext(Dispatchers.Main) {
                        isWorking = false
                        statusText = result
                    }
                }
            },
            enabled = !isWorking && url.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("DOWNLOAD & CONVERT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

private fun sendDownloadRequest(
    url: String,
    choice: Int,
    searchQuery: String,
    clientId: String,
    clientSecret: String
): String {
    return try {
        val jsonPayload = JSONObject().apply {
            put("url", url)
            put("choice", choice)
            put("search_query", searchQuery)
            put("client_id", clientId)
            put("client_secret", clientSecret)
        }.toString()

        val apiUrl = URL("http://127.0.0.1:8080/api/fetch-audio")
        val conn = apiUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 300000

        conn.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(Charsets.UTF_8))
        }

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            if (jsonResponse.optString("status") == "Success") {
                "Success! Saved to Download/MyConverter"
            } else {
                "Error: ${jsonResponse.optString("error", "Unknown error")}"
            }
        } else {
            val errResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            "Server Error: $errResponse"
        }
    } catch (e: Exception) {
        "Failed to connect to Termux backend: ${e.message}"
    }
}
