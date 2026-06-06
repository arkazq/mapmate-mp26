package com.mapmate.presentation.common

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.mapmate.R

enum class MapMateIconType(
    @param:DrawableRes val drawableRes: Int,
) {
    Notifications(R.drawable.ic_mapmate_notifications),
    Home(R.drawable.ic_mapmate_home),
    Routines(R.drawable.ic_mapmate_list),
    Records(R.drawable.ic_mapmate_records),
    Settings(R.drawable.ic_mapmate_settings),
    Add(R.drawable.ic_mapmate_add),
    Back(R.drawable.ic_mapmate_arrow_back),
    Time(R.drawable.ic_mapmate_schedule),
    Bus(R.drawable.ic_mapmate_bus),
    Walk(R.drawable.ic_mapmate_walk),
    Car(R.drawable.ic_mapmate_car),
    Person(R.drawable.ic_mapmate_person),
    Shield(R.drawable.ic_mapmate_shield),
    Edit(R.drawable.ic_mapmate_edit),
    Delete(R.drawable.ic_mapmate_delete),
    More(R.drawable.ic_mapmate_more_vert),
    School(R.drawable.ic_mapmate_school),
    Work(R.drawable.ic_mapmate_work),
    Check(R.drawable.ic_mapmate_check),
    Location(R.drawable.ic_mapmate_location),
    Flag(R.drawable.ic_mapmate_flag),
    Play(R.drawable.ic_mapmate_play),
    Route(R.drawable.ic_mapmate_route),
}

@Composable
fun MapMateIcon(
    icon: MapMateIconType,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(id = icon.drawableRes),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

fun transportModeIcon(transportMode: com.mapmate.domain.model.TransportMode): MapMateIconType {
    return when (transportMode) {
        com.mapmate.domain.model.TransportMode.TRANSIT -> MapMateIconType.Bus
        com.mapmate.domain.model.TransportMode.WALK -> MapMateIconType.Walk
        com.mapmate.domain.model.TransportMode.CAR -> MapMateIconType.Car
    }
}
