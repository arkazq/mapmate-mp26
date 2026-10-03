package com.mapmate.domain.model

fun Destination.hasValidCoordinates(): Boolean =
    latitude?.let { it.isFinite() && it in -90.0..90.0 } == true &&
        longitude?.let { it.isFinite() && it in -180.0..180.0 } == true
