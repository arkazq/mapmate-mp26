package com.mapmate.presentation.common

import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.TransportMode

fun RepeatDay.toKoreanShortLabel(): String {
    return when (this) {
        RepeatDay.MONDAY -> "월"
        RepeatDay.TUESDAY -> "화"
        RepeatDay.WEDNESDAY -> "수"
        RepeatDay.THURSDAY -> "목"
        RepeatDay.FRIDAY -> "금"
        RepeatDay.SATURDAY -> "토"
        RepeatDay.SUNDAY -> "일"
    }
}

fun TransportMode.toKoreanLabel(): String {
    return when (this) {
        TransportMode.TRANSIT -> "대중교통"
        TransportMode.WALK -> "도보"
        TransportMode.CAR -> "자동차"
    }
}

fun TransportMode.toKoreanDescription(): String {
    return when (this) {
        TransportMode.TRANSIT -> "버스/지하철"
        TransportMode.WALK -> "도보 이동"
        TransportMode.CAR -> "차량 이동"
    }
}
