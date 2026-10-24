package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayGroup(
    val dayKey: Long,
    val dayLabel: String,
    val items: List<MediaItem>
)

fun groupMediaByDay(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = todayCal.timeInMillis
    val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
    val currentYear = todayCal.get(Calendar.YEAR)

    val dayFormatSameYear = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    val dayFormatOtherYear = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    val groupedMap = LinkedHashMap<Long, MutableList<MediaItem>>()

    for (item in media) {
        val timestamp = if (item.dateTakenMillis > 0) item.dateTakenMillis else (item.dateAddedSeconds * 1000L)
        val validTimestamp = if (timestamp > 0) timestamp else todayStart
        cal.timeInMillis = validTimestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis

        groupedMap.getOrPut(dayStart) { mutableListOf() }.add(item)
    }

    return groupedMap.entries
        .sortedByDescending { it.key }
        .map { (dayStart, dayItems) ->
            val label = when (dayStart) {
                todayStart -> "Today"
                yesterdayStart -> "Yesterday"
                else -> {
                    cal.timeInMillis = dayStart
                    if (cal.get(Calendar.YEAR) == currentYear) {
                        dayFormatSameYear.format(Date(dayStart))
                    } else {
                        dayFormatOtherYear.format(Date(dayStart))
                    }
                }
            }
            DayGroup(dayKey = dayStart, dayLabel = label, items = dayItems)
        }
}

fun groupMediaByMonth(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()

    val cal = Calendar.getInstance()
    val todayCal = Calendar.getInstance()
    val currentYear = todayCal.get(Calendar.YEAR)

    val monthFormatSameYear = SimpleDateFormat("MMMM", Locale.getDefault())
    val monthFormatOtherYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val groupedMap = LinkedHashMap<Long, MutableList<MediaItem>>()

    for (item in media) {
        val timestamp = if (item.dateTakenMillis > 0) item.dateTakenMillis else (item.dateAddedSeconds * 1000L)
        val validTimestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()
        cal.timeInMillis = validTimestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis

        groupedMap.getOrPut(monthStart) { mutableListOf() }.add(item)
    }

    return groupedMap.entries
        .sortedByDescending { it.key }
        .map { (monthStart, monthItems) ->
            cal.timeInMillis = monthStart
            val label = if (cal.get(Calendar.YEAR) == currentYear) {
                monthFormatSameYear.format(Date(monthStart))
            } else {
                monthFormatOtherYear.format(Date(monthStart))
            }
            DayGroup(dayKey = monthStart, dayLabel = label, items = monthItems)
        }
}

fun groupMediaByYear(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    val groupedMap = LinkedHashMap<Long, MutableList<MediaItem>>()
    for (item in media) {
        val timestamp = if (item.dateTakenMillis > 0) item.dateTakenMillis else (item.dateAddedSeconds * 1000L)
        val validTimestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()
        cal.timeInMillis = validTimestamp
        cal.set(Calendar.MONTH, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val yearStart = cal.timeInMillis
        groupedMap.getOrPut(yearStart) { mutableListOf() }.add(item)
    }
    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    return groupedMap.entries
        .sortedByDescending { it.key }
        .map { (yearStart, yearItems) ->
            DayGroup(dayKey = yearStart, dayLabel = yearFormat.format(Date(yearStart)), items = yearItems)
        }
}

fun groupMediaByFolder(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()
    var syntheticKey = 0L
    return media.groupBy {
        it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" }
    }.map { (folder, folderItems) ->
        DayGroup(dayKey = syntheticKey++, dayLabel = folder, items = folderItems)
    }
}

fun groupMediaByInitial(media: List<MediaItem>): List<DayGroup> {
    if (media.isEmpty()) return emptyList()
    var syntheticKey = 0L
    return media.groupBy {
        val firstChar = it.name.trim().firstOrNull()?.uppercaseChar()
        if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
    }.toSortedMap().map { (initial, initialItems) ->
        DayGroup(dayKey = syntheticKey++, dayLabel = initial, items = initialItems)
    }
}
