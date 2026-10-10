package com.example.compressor.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.compressor.data.CompressionEngine
import com.example.compressor.data.CompressionResult
import com.example.compressor.data.SelectedFile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { CompressionEngine(context) }

    var selectedFiles by remember { mutableStateOf<List<SelectedFile>>(emptyList()) }
    var currentFileToCompress by remember { mutableStateOf<SelectedFile?>(null) }
    var compressionQuality by remember { mutableFloatStateOf(70f) }
    var isProcessing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<CompressionResult>>(emptyList()) }

    // Picker for creating output file in destination directory chosen by user
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destinationUri: Uri? ->
        if (destinationUri != null && currentFileToCompress != null) {
            isProcessing = true
            coroutineScope.launch {
                val res = engine.compressFileToUri(
                    file = currentFileToCompress!!,
                    destinationUri = destinationUri,
                    quality = compressionQuality.toInt()
                )
                results = results + res
                isProcessing = false
                currentFileToCompress = null
            }
        }
    }

    // Multi-File Picker to select input files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val fileList = uris.mapNotNull { uri ->
            var name = "file"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                cursor.moveToFirst()
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
            val isPdf = name.endsWith(".pdf", ignoreCase = true)
            SelectedFile(uri, name, isPdf, size)
        }
        selectedFiles = fileList
        results = emptyList()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Local File Compressor") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Images & PDFs")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedFiles.isNotEmpty()) {
                Text("Compression Level: ${compressionQuality.toInt()}%")
                Slider(
                    value = compressionQuality,
                    onValueChange = { compressionQuality = it },
                    valueRange = 10f..90f,
                    steps = 8
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(selectedFiles) { file ->
                    val result = results.find { it.fileName == file.name }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = file.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Original Size: ${file.originalSizeBytes / 1024} KB")

                            if (result != null) {
                                Text(text = "Compressed Size: ${result.compressedSize / 1024} KB")
                                Text(
                                    text = "Saved to selected location",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        currentFileToCompress = file
                                        // Launch directory/save selector picker
                                        saveFileLauncher.launch("compressed_${file.name}")
                                    },
                                    enabled = !isProcessing
                                ) {
                                    Text("Choose Location & Compress")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}