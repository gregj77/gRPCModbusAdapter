package com.gcs.gRPCModbusAdapter

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class Color(i:Int){
    BLACK(0),
    BROWN(1),
    RED(2),
    ORANGE(3),
    YELLOW(4),
    GREEN(5),
    BLUE(6),
    VIOLET(7),
    GREY(8),
    WHITE(9)
}

enum class Unit {
    OHMS, KILOOHMS, MEGAOHMS, GIGAOHMS, TERAOHMS, PETAOHMS, EXAOHMS
}

object ResistorColorTrio {


    fun isValid(number: String): Boolean {
        if (number.length <= 1) {
            throw Exception("invalid input")
        }

        if (number.asSequence().any { !it.isDigit() || !it.isWhitespace() }) {
            throw Exception("invalid format")
        }

        val sum = number.asSequence()
            .filter { it.isDigit() }
            .map { it - '0' }
            .map { it * 2 }
            .map { if (it > 9) { it - 9 } else { it } }
            .sum()

        return (sum % 10 == 0)
    }
}