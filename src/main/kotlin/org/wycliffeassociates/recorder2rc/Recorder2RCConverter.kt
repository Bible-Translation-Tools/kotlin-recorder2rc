package org.wycliffeassociates.recorder2rc

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.wycliffeassociates.org.wycliffeassociates.recorder2rc.SourceBuilder
import org.wycliffeassociates.org.wycliffeassociates.recorder2rc.concatAudio
import org.wycliffeassociates.recorder2rc.recorderentity.Book
import org.wycliffeassociates.recorder2rc.recorderentity.Language
import org.wycliffeassociates.recorder2rc.recorderentity.Manifest
import org.wycliffeassociates.resourcecontainer.ResourceContainer
import org.wycliffeassociates.resourcecontainer.entity.Checking
import org.wycliffeassociates.resourcecontainer.entity.DublinCore
import org.wycliffeassociates.resourcecontainer.entity.Project
import org.wycliffeassociates.resourcecontainer.entity.Source
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class Recorder2RCConverter {

    fun convert(inputFile: File, outputDir: File): File {
        if (!outputDir.exists()) outputDir.mkdirs()
        val tempDir = Files.createTempDirectory(outputDir.toPath(), "extract-temp").toFile()
        unzipFile(inputFile, destinationDir = tempDir)

        val projectDir = tempDir.walk().find { it.name == "manifest.json" }!!.parentFile
        val rcDir = outputDir.resolve(inputFile.nameWithoutExtension)
        rcDir.mkdirs()

        // build manifest
        val mapper = ObjectMapper(JsonFactory()).registerKotlinModule()
        val manifestFile = projectDir.resolve("manifest.json")
        val recorderManifest = mapper.readValue<Manifest>(manifestFile)
        val manifest = buildManifest(recorderManifest, rcDir)
        writeManifest(manifest, rcDir)

        val selectedTakes: List<String> = mapper.readValue(projectDir.resolve("selected.json"))
        val selectedTakesFile = rcDir.resolve(".apps/orature/selected.txt")
            .apply { createNewFile() } // resembles ongoing project

        val chapterDigitFormat = if (recorderManifest.chapters.size <= 99) "%02d" else "%03d"
        /* handle recorded takes */
        recorderManifest.chapters.forEach { chapter ->
            // create chapter folder
            val chapterPathName = String.format(chapterDigitFormat, chapter.chapterNumber)
            val chapterDirInRC = rcDir.resolve(".apps/orature/takes/c${chapterPathName}")
            chapterDirInRC.mkdir()

            val takesToCompile = mutableListOf<File>()
            // copy take files over
            chapter.chunks
                .sortedBy { it.start }
                .forEach { chunk ->
                    chunk.takes
                        .filter { it.name in selectedTakes }
                        .forEach { take ->
                            val takeFile = projectDir.resolve(chapterPathName).resolve(take.name)
                            val rcTakeName = buildOratureTakeName(
                                recorderManifest.language.slug,
                                recorderManifest.version.slug,
                                recorderManifest.book.slug,
                                chapterPathName,
                                chunk.start,
                                "_t(\\d+)".toRegex().find(take.name)?.groupValues?.get(1)?.toInt() ?: 1
                            )
                            val takeFileInRC = chapterDirInRC.resolve(rcTakeName)
                            takeFile.copyTo(takeFileInRC)
                            selectedTakesFile.appendText("c$chapterPathName/$rcTakeName\n")
                            takesToCompile.add(takeFileInRC)
                        }
                }

            val chapterTakeName = buildOratureTakeName(
                recorderManifest.language.slug,
                recorderManifest.version.slug,
                recorderManifest.book.slug,
                chapterPathName,
                null,
                1
            )

            if (chapterDirInRC.list().isEmpty()) {
                chapterDirInRC.delete()
            } else {
                val chapterTake = chapterDirInRC.resolve(chapterTakeName)
                chapterTake.createNewFile()
                concatAudio(takesToCompile, chapterTake)
                selectedTakesFile.appendText("c$chapterPathName/$chapterTakeName\n")
            }

        }

        val zippedFile = outputDir.resolve("${rcDir.name}.zip")
        zipDirectory(rcDir, zippedFile)

//        rcDir.deleteRecursively()
        tempDir.deleteRecursively()

        return zippedFile
    }

    private fun buildManifest(recorderManifest: Manifest, rcPath: File): RCManifest {
        val sourceDir = rcPath.resolve(".apps/orature/source").apply { mkdirs() }
        val sourceFile = SourceBuilder.createMatchingSourceForLanguage(recorderManifest.sourceLanguage!!.slug, sourceDir)!!
        val sourceLanguage = recorderManifest.sourceLanguage.slug
        val sourceVersion = ResourceContainer.load(sourceFile).use { it.manifest.dublinCore.version }
        val source = Source(identifier = "ulb", language = sourceLanguage, version = sourceVersion)
        val sourceList = listOfNotNull(source).toMutableList()

        val dublinCore = DublinCore(
            type = "book",
            conformsTo = "rc0.2",
            format = "audio/wav",
            identifier = recorderManifest.version.slug,
            title = recorderManifest.version.name,
            subject = "Bible",
            description = "",
            language = mapToRCLanguage(recorderManifest.language),
            source = sourceList,
            rights = "CC BY-SA 4.0",
            creator = "Orature",
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
            path = "./content",
            versification = "eng"
        )
    }

    private fun mapToRCLanguage(language: Language): RCLanguage {
        return RCLanguage(identifier = language.slug, title = language.name)
    }

    private fun buildOratureTakeName(
        language: String,
        resource: String,
        book: String,
        chapter: String,
        verse: Int? = null,
        take: Int,
        extension: String = "wav"
    ): String {
        return "${language}_${resource}_${book}_c${chapter}_${verse?.let { "v$verse" } ?: "meta"}_t$take.$extension"
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

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        zipFile.createNewFile()
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryPath = file.relativeTo(sourceDir).invariantSeparatorsPath
                    val zipEntry = ZipEntry(entryPath)
                    zos.putNextEntry(zipEntry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                }
            }
        }
    }
}