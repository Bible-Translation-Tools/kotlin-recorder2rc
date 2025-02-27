package org.wycliffeassociates.recorder2rc.recorderentity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Take(
    val name: String,
    val rating: Int
)