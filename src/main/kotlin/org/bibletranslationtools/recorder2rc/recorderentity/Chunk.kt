package org.bibletranslationtools.recorder2rc.recorderentity

import com.fasterxml.jackson.annotation.JsonProperty

data class Chunk(
    @JsonProperty("startv")
    val start: Int,
    @JsonProperty("endv")
    val end: Int,
    val takes: List<Take>
)