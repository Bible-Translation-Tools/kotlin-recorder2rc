package org.wycliffeassociates.recorder2rc

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.wycliffeassociates.recorder2rc.recorderentity.Book
import org.wycliffeassociates.recorder2rc.recorderentity.Language
import org.wycliffeassociates.recorder2rc.recorderentity.Manifest
import org.wycliffeassociates.recorder2rc.recorderentity.Take
import org.wycliffeassociates.resourcecontainer.entity.Checking
import org.wycliffeassociates.resourcecontainer.entity.DublinCore
import org.wycliffeassociates.resourcecontainer.entity.Project
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.regex.Pattern
import java.util.zip.ZipFile

class Recorder2RCConverter {

    fun convert(inputFile: File, outputDir: File): File {
        if (!outputDir.exists()) outputDir.mkdirs()
        val tempDir = outputDir.resolve("extract-temp").also { it.mkdir() }
        unzipFile(inputFile, destinationDir = tempDir)

        val projectDir = tempDir.let {
            it.listFiles()?.singleOrNull() ?: tempDir
        }

        val rcDir = outputDir.resolve(inputFile.nameWithoutExtension)
        rcDir.mkdirs()
//        val outputFile = outputDir.resolve("${inputFile.nameWithoutExtension}.zip")


        // build manifest
        val mapper = ObjectMapper(JsonFactory()).registerKotlinModule()
        val manifestFile = projectDir.resolve("manifest.json")
        val recorderManifest = mapper.readValue<Manifest>(manifestFile)
        val manifest = buildManifest(recorderManifest)
        writeManifest(manifest, rcDir)

        recorderManifest.chapters.forEach { chapter ->
            // create chapter folder
            val chapterPathName = String.format("%02d", chapter.chapterNumber)
            val chapterDir = rcDir.resolve(chapterPathName)
            chapterDir.mkdir()
            // copy take files over
            chapter.chunks.forEach { chunk ->
                chunk.takes.forEach { take ->
                    val takeFile = projectDir.resolve(chapterPathName).resolve(take.name)
                    val rcTakeName = buildOratureTakeName(
                        recorderManifest.language.slug,
                        recorderManifest.version.slug,
                        recorderManifest.book.slug,
                        chapter.chapterNumber,
                        chunk.start,
                        "_t(\\d+)".toRegex().find(take.name)?.groupValues?.get(1)?.toInt() ?: 1
                    )
                    val takeFileInRC = rcDir.resolve(".apps/orature/takes/c${chapterPathName}/$rcTakeName")
                    takeFile.copyTo(takeFileInRC)
                }
            }

            if (chapterDir.list().isEmpty()) {
                chapterDir.delete()
            }

        }

        return outputDir
    }

    private fun buildManifest(recorderManifest: Manifest): RCManifest {
        val dublinCore = DublinCore(
            type = "book",
            conformsTo = "rc0.2",
            format = "audio/wav",
            identifier = "ulb",
            title = "ULB",
            subject = "Bible",
            description = "",
            language = mapToRCLanguage(recorderManifest.language),
            source = mutableListOf(),
            rights = "CC BY-SA 4.0",
            creator = "Recorder",
            contributor = mutableListOf(),
            relation = mutableListOf(),
            publisher = "Wycliffe Associates",
            issued = LocalDate.now().toString(),
            modified = LocalDate.now().toString(),
            version = "1"

        )
        val manifest = RCManifest(
            dublinCore = dublinCore,
            checking = Checking(
                checkingEntity = listOf("Wycliffe Associates"),
                checkingLevel = "1"
            ),
            projects = mutableListOf(rcProject(recorderManifest.book))
        )
        return manifest
    }

    private fun writeManifest(manifest: RCManifest, outDir: File) {
        val mapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()
        mapper.writeValue(outDir.resolve("manifest.yaml"), manifest)
    }

    private fun rcProject(book: Book): Project {
        return Project(
            identifier = book.slug,
            title = book.name,
            sort = book.sort,
            path = "./",
            versification = "eng"
        )
    }

    private fun convertTakeFile(takes: List<Take>) {

    }

    private fun mapToRCLanguage(language: Language): RCLanguage {
        return RCLanguage(identifier = language.slug, title = language.name)
    }

    private fun buildOratureTakeName(
        language: String,
        resource: String,
        book: String,
        chapter: Int,
        verse: Int? = null,
        take: Int,
        extension: String = "wav"
    ): String {
        return "${language}_${resource}_${book}_c${chapter}_${verse?.let { "v$verse" } ?: "meta" }_t$take.$extension"
    }

    private fun unzipFile(file: File, destinationDir: File) {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = destinationDir.resolve(entry.name)
                entryDestination.parentFile.mkdirs()
                if (entry.isDirectory) {
                    entryDestination.mkdir()
                } else {
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(entryDestination.toPath()).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}