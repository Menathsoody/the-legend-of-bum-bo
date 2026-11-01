package com.papergrimoire.save

/** Anything that can flatten itself to a single line of save text. */
interface Saveable {
    fun saveToString(): String
}
