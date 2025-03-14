package org.bibletranslationtools.recorder2rc.recorderentity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Manifest(
    val language: Language,
    @JsonProperty("source_language")
    val sourceLanguage: Language?,
    val book: Book,
    val version: Version,
    val anthology: Anthology,
    val mode: Mode,
    @JsonProperty("manifest")
    val chapters: List<Chapter>
)