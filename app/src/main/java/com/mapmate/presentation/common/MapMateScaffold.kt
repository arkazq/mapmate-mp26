package com.mapmate.presentation.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mapmate.ui.theme.MapMateTheme

enum class MapMateBottomDestination(val label: String, val icon: MapMateIconType) {
    Home("홈", MapMateIconType.Home),
    Routines("루틴", MapMateIconType.Routines),
    Records("기록", MapMateIconType.Records),
    Settings("설정", MapMateIconType.Settings),
}

@Composable
fun MapMateScaffold(
    selectedDestination: MapMateBottomDestination?,
    onDestinationSelected: (MapMateBottomDestination) -> Unit,
    modifier: Modifier = Modifier,
    showBottomBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar && selectedDestination != null) {
                MapMateBottomBar(selectedDestination, onDestinationSelected)
            }
        },
        content = content,
    )
}

@Composable
fun MapMateBottomBar(
    selectedDestination: MapMateBottomDestination,
    onDestinationSelected: (MapMateBottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        MapMateBottomDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = { MapMateIcon(destination.icon, null, Modifier.size(24.dp)) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MapMateBottomBarPreview() {
    MapMateTheme { MapMateBottomBar(MapMateBottomDestination.Home, {}) }
}
