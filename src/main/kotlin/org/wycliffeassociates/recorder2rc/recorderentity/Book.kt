package org.wycliffeassociates.recorder2rc.recorderentity

data class Book(
    val name: String,
    val slug: String,
    val number: String
) {
    val sort = number.toInt()
}