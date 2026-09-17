package com.zambiotica.photoframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleBagTest {

    @Test
    fun `una vuelta completa muestra todas las fotos una sola vez`() {
        val bag = ShuffleBag(200, Random(42))
        val seen = (1..200).map { bag.next() }
        assertEquals(200, seen.toSet().size)
        assertTrue(seen.all { it in 0..199 })
    }

    @Test
    fun `al barajar de nuevo no repite la ultima foto mostrada`() {
        repeat(50) { seed ->
            val bag = ShuffleBag(10, Random(seed.toLong()))
            var last = -1
            repeat(10) { last = bag.next() }
            assertNotEquals(last, bag.next())
        }
    }

    @Test
    fun `previous vuelve a la foto anterior`() {
        val bag = ShuffleBag(5, Random(7))
        val first = bag.next()
        val second = bag.next()
        assertNotEquals(first, second)
        assertEquals(first, bag.previous())
    }

    @Test
    fun `con la carpeta vacia devuelve menos uno y no revienta`() {
        val bag = ShuffleBag(0)
        assertEquals(-1, bag.next())
        assertEquals(-1, bag.previous())
    }

    @Test
    fun `con una sola foto la repite sin error`() {
        val bag = ShuffleBag(1)
        assertEquals(0, bag.next())
        assertEquals(0, bag.next())
    }

    @Test
    fun `resize adapta la bolsa a la carpeta nueva`() {
        val bag = ShuffleBag(3, Random(1))
        bag.next()
        bag.resize(200)
        assertEquals(200, bag.size)
        assertTrue((1..200).map { bag.next() }.toSet().size == 200)
    }
}
