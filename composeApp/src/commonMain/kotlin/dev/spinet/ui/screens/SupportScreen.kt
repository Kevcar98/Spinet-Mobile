package dev.spinet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spinet.AppBranding
import dev.spinet.util.openUrlExternally
import dev.spinet.resources.Res
import dev.spinet.resources.kofi
import dev.spinet.resources.liberapay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun SupportScreen(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { HeroHeader() }

        item { SectionTitle("Support Spinet") }
        item { DonateLinks() }

        item { SectionTitle("The project") }
        item { ProjectLinks() }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeroHeader() {
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(24.dp)
        ) {
            Pill(
                icon = Icons.Filled.Groups,
                text = "FREE AND OPEN SOURCE",
                container = Color.White.copy(alpha = 0.20f),
                content = Color.White
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = AppBranding.NAME,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A free, open-source music player. No ads, no paywalls, no accounts. " +
                    "Bug reports and ideas are welcome on GitHub.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { openUrlExternally(AppBranding.REPO) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.Star, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Star on GitHub", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProjectLinks() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RepoRow(
            title = "Spinet for PC",
            subtitle = "Windows. macOS and Linux can build it from source",
            url = AppBranding.DESKTOP_REPO,
            isThisApp = AppBranding.REPO == AppBranding.DESKTOP_REPO
        )
        RepoRow(
            title = "Spinet for Android",
            subtitle = "The phone build",
            url = AppBranding.MOBILE_REPO,
            isThisApp = AppBranding.REPO == AppBranding.MOBILE_REPO
        )
        RepoRow(
            title = "Spinet Server",
            subtitle = "Optional: host your own library and sync between devices",
            url = AppBranding.SERVER_REPO,
            isThisApp = false
        )
    }
}

@Composable
private fun DonateLinks() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Spinet is free. If you'd like to help with its upkeep, you can make a donation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DonateRow(title = "Ko-fi", subtitle = "One-off or monthly donations", url = KOFI_URL, logo = Res.drawable.kofi, tile = Color.White)
        DonateRow(title = "Liberapay", subtitle = "Weekly or monthly donations", url = LIBERAPAY_URL, logo = Res.drawable.liberapay, tile = LIBERAPAY_YELLOW)
    }
}

@Composable
private fun DonateRow(title: String, subtitle: String, url: String, logo: DrawableResource, tile: Color) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tile).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(logo), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { openUrlExternally(url) }) {
                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Donate")
            }
        }
    }
}

private const val KOFI_URL = "https://ko-fi.com/spinet"
private const val LIBERAPAY_URL = "https://liberapay.com/Spinet/"
private val LIBERAPAY_YELLOW = Color(0xFFF6C915)

@Composable
private fun RepoRow(title: String, subtitle: String, url: String, isThisApp: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isThisApp) {
                        Spacer(Modifier.width(8.dp))
                        Pill(
                            icon = Icons.Filled.CheckCircle,
                            text = "This app",
                            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            content = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { openUrlExternally(url) }) {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Star")
            }
        }
    }
}
