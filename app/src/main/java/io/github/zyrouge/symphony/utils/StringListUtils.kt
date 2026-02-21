package io.github.zyrouge.symphony.utils

import io.github.zyrouge.symphony.PathSortBy

object StringListUtils {
    fun sort(values: List<String>, by: PathSortBy, reverse: Boolean): List<String> {
        val sorted = when (by) {
            PathSortBy.PATH_SORT_CUSTOM -> values
            else -> values.sorted()
        }
        return if (reverse) sorted.reversed() else sorted
    }
}
