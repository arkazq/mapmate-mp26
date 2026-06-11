package com.mapmate.domain.model

data class AppSettings(
    val personalBufferMinutes: Int = DEFAULT_PERSONAL_BUFFER_MINUTES,
    val safetyMarginMinutes: Int = DEFAULT_SAFETY_MARGIN_MINUTES,
    val notificationsEnabled: Boolean = DEFAULT_NOTIFICATIONS_ENABLED,
    val defaultTransportMode: TransportMode = DEFAULT_TRANSPORT_MODE,
) {
    companion object {
        const val DEFAULT_PERSONAL_BUFFER_MINUTES = 6
        const val DEFAULT_SAFETY_MARGIN_MINUTES = 5
        const val DEFAULT_NOTIFICATIONS_ENABLED = false
        const val MIN_BUFFER_MINUTES = 0
        const val MAX_BUFFER_MINUTES = 60
        const val MAX_AUTO_BUFFER_ADJUSTMENT_MINUTES = 5
        val DEFAULT_TRANSPORT_MODE = TransportMode.TRANSIT

        fun isValidBufferMinutes(minutes: Int): Boolean {
            return minutes in MIN_BUFFER_MINUTES..MAX_BUFFER_MINUTES
        }

        fun bufferMinutesOrDefault(
            minutes: Int?,
            defaultMinutes: Int,
        ): Int {
            return minutes?.takeIf(::isValidBufferMinutes) ?: defaultMinutes
        }

        fun adjustedPersonalBufferMinutes(
            currentMinutes: Int,
            arrivalDeltaMinutes: Int,
        ): Int {
            val adjustment = arrivalDeltaMinutes.coerceIn(
                minimumValue = -MAX_AUTO_BUFFER_ADJUSTMENT_MINUTES,
                maximumValue = MAX_AUTO_BUFFER_ADJUSTMENT_MINUTES,
            )
            return (currentMinutes + adjustment).coerceIn(
                minimumValue = MIN_BUFFER_MINUTES,
                maximumValue = MAX_BUFFER_MINUTES,
            )
        }

        fun transportModeOrDefault(storedName: String?): TransportMode {
            return TransportMode.entries.firstOrNull { it.name == storedName } ?: DEFAULT_TRANSPORT_MODE
        }
    }
}
