package dev.spinet.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SaveJsonFileButton(label: String, suggestedName: String, content: () -> String)

@Composable
expect fun rememberJsonOpener(onFileRead: (String) -> Unit): () -> Unit

@Composable
expect fun OpenCsvFileButton(label: String, onFileRead: (String) -> Unit)

@Composable
expect fun rememberJsonSaver(): (suggestedName: String, content: String) -> Unit
