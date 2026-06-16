package com.mapmate.domain.alarm

interface PredepartureStatusNotificationPublisher {
    fun show(schedule: DepartureAlarmSchedule)

    fun cancel(routineId: Long)

    fun cancelAll(routineIds: Collection<Long>)

    object NoOp : PredepartureStatusNotificationPublisher {
        override fun show(schedule: DepartureAlarmSchedule) = Unit

        override fun cancel(routineId: Long) = Unit

        override fun cancelAll(routineIds: Collection<Long>) = Unit
    }
}
