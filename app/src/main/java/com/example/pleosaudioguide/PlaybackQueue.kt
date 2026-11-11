package com.example.pleosaudioguide

/** 항목 하나를 꺼내 목표 위치에 삽입하고 나머지 항목의 상대 순서는 유지한다. */
object PlaybackQueue {
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
}
