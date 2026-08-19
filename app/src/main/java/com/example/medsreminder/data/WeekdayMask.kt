package com.example.medsreminder.data

import java.time.DayOfWeek

object WeekdayMask {
    const val ALL = 0x7f

    fun isValid(mask: Int): Boolean = mask in 1..ALL

    fun requireValid(mask: Int) {
        require(isValid(mask)) { "Weekday mask must contain only Monday-Sunday bits and select at least one day" }
    }

    fun contains(mask: Int, dayOfWeek: DayOfWeek): Boolean {
        requireValid(mask)
        return mask and (1 shl (dayOfWeek.value - 1)) != 0
    }
}
