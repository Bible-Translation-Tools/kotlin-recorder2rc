package org.wycliffeassociates.org.wycliffeassociates.recorder2rc

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File


private data class SourceMapping(
    val languageCode: String,
    val matchingSource: String
)

object SourceBuilder {
    private var languageToSourceMappings: Map<String, String> = mapOf()

    init {
        javaClass.classLoader.getResourceAsStream("gl_sources.json")?.use { stream ->
            val mapper = ObjectMapper(JsonFactory()).registerKotlinModule()
            val mappings: List<SourceMapping> = mapper.readValue(stream)
            languageToSourceMappings = mappings.associate { Pair(it.languageCode, it.matchingSource) }
        }
    }

    fun createMatchingSourceForLanguage(languageCode: String, outputDir: File): File? {
        val resourceName = languageToSourceMappings[languageCode]
        resourceName?.let {
            val stream = javaClass.classLoader.getResourceAsStream("sources/$resourceName.zip")
            val targetFile = outputDir.resolve("$resourceName.zip")
            targetFile.outputStream().use { out ->
                stream.copyTo(out)
            }
            return targetFile
        } ?: return null
    }
}