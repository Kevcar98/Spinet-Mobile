package dev.spinet.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Filled.LibraryMusic),
    Search("Search", Icons.Filled.Search),
    Settings("Settings", Icons.Filled.Settings),
    Support("Support", Icons.Filled.Favorite)
}
