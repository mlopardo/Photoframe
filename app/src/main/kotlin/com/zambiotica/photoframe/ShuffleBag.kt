package com.zambiotica.photoframe

import kotlin.random.Random

/**
 * Orden aleatorio sin repetir: baraja todos los índices y los entrega uno por uno.
 * Al agotarse vuelve a barajar, evitando que la primera de la vuelta nueva sea
 * la última que se mostró.
 *
 * Kotlin puro, sin dependencias de Android: es lo que verifica el test del CI.
 */
class ShuffleBag(
    size: Int,
    private val random: Random = Random.Default
) {
    private var order: MutableList<Int> = MutableList(size) { it }
    private var cursor: Int = -1

    init {
        reshuffle(avoidFirst = -1)
    }

    val size: Int get() = order.size

    fun next(): Int {
        if (order.isEmpty()) return -1
        if (cursor >= order.lastIndex) {
            val last = if (cursor in order.indices) order[cursor] else -1
            reshuffle(avoidFirst = last)
            cursor = 0
        } else {
            cursor++
        }
        return order[cursor]
    }

    fun previous(): Int {
        if (order.isEmpty()) return -1
        cursor = if (cursor <= 0) 0 else cursor - 1
        return order[cursor]
    }

    fun resize(newSize: Int) {
        order = MutableList(newSize) { it }
        cursor = -1
        reshuffle(avoidFirst = -1)
    }

    private fun reshuffle(avoidFirst: Int) {
        if (order.size <= 1) return
        order.shuffle(random)
        if (avoidFirst >= 0 && order.first() == avoidFirst) {
            val swapWith = 1 + random.nextInt(order.size - 1)
            val tmp = order[0]
            order[0] = order[swapWith]
            order[swapWith] = tmp
        }
    }
}
