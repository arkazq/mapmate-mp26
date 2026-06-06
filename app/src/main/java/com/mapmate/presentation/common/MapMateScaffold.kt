package com.mapmate.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mapmate.ui.theme.MapMateTheme

enum class MapMateBottomDestination(
    val label: String,
    val icon: MapMateIconType,
) {
    Home(label = "홈", icon = MapMateIconType.Home),
    Routines(label = "루틴", icon = MapMateIconType.Routines),
    Records(label = "기록", icon = MapMateIconType.Records),
    Settings(label = "설정", icon = MapMateIconType.Settings),
}

@Composable
fun MapMateScaffold(
    selectedDestination: MapMateBottomDestination?,
    onDestinationSelected: (MapMateBottomDestination) -> Unit,
    modifier: Modifier = Modifier,
    showBottomBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar && selectedDestination != null) {
                    MapMateBottomBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = onDestinationSelected,
                    )
                }
            },
            content = content,
        )
    }
}

@Composable
fun MapMateBottomBar(
    selectedDestination: MapMateBottomDestination,
    onDestinationSelected: (MapMateBottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MapMateElevation.BottomBar,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapMateBottomDestination.entries.forEach { destination ->
                BottomBarItem(
                    destination = destination,
                    selected = destination == selectedDestination,
                    onClick = { onDestinationSelected(destination) },
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: MapMateBottomDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(54.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(42.dp)
                    .height(26.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                } else {
                    Color.Transparent
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MapMateIcon(
                        icon = destination.icon,
                        contentDescription = destination.label,
                        modifier = Modifier.size(21.dp),
                        tint = contentColor,
                    )
                }
            }
            Text(
                text = destination.label,
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MapMateBottomBarPreview() {
    MapMateTheme {
        MapMateBottomBar(
            selectedDestination = MapMateBottomDestination.Home,
            onDestinationSelected = {},
        )
    }
}
