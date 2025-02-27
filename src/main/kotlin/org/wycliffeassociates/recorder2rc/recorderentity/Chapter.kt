package org.wycliffeassociates.recorder2rc.recorderentity

import com.fasterxml.jackson.annotation.JsonProperty

data class Chapter(
    @JsonProperty("chapter")
    val chapterNumber: Int,
    @JsonProperty("checking_level")
    val checkingLevel: Int,
    val chunks: List<Chunk>
)
