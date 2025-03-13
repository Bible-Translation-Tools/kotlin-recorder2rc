package org.bibletranslationtools.recorder2rc

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import org.bibletranslationtools.recorder2rc.recorderentity.Book
import org.bibletranslationtools.recorder2rc.recorderentity.Language
import org.bibletranslationtools.recorder2rc.recorderentity.Manifest
import org.wycliffeassociates.resourcecontainer.ResourceContainer
import org.wycliffeassociates.resourcecontainer.entity.Checking
import org.wycliffeassociates.resourcecontainer.entity.DublinCore
import org.wycliffeassociates.resourcecontainer.entity.Project
import org.wycliffeassociates.resourcecontainer.entity.Source
import org.wycliffeassociates.resourcecontainer.errors.UnsupportedRCException
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.LocalDate

class Recorder2RCConverter {
    /* Recorder-specific */
    private val RECORDER_MANIFEST = "manifest.json"
    private val RECORDER_SELECTED_FILE = "selected.json"

    /* Orature-specific */
    private val ORATURE_INTERNAL_DIR = ".apps/orature"
    private val TAKE_DIR = "$ORATURE_INTERNAL_DIR/takes"
    private val SOURCE_DIR = "$ORATURE_INTERNAL_DIR/source"
    private val ORATURE_SELECTED_TAKES_FILE = "$ORATURE_INTERNAL_DIR/selected.txt"
    private val ORATURE_MANIFEST = "manifest.yaml"

    private val versification: Versification = javaClass.classLoader.getResourceAsStream("eng.json")?.use { stream ->
        val mapper = ObjectMapper(JsonFactory()).registerKotlinModule()
        val jsonVers = mapper.readTree(stream).get("maxVerses")
        return@use mapper.treeToValue<Versification>(jsonVers)
    } ?: throw FileNotFoundException("Versification file not found.")

    fun convert(inputFile: File, outputDir: File): File {
        if (!outputDir.exists()) outputDir.mkdirs()
        val tempDir = Files.createTempDirectory(outputDir.toPath(), "extract-temp").toFile()
        unzipFile(inputFile, destinationDir = tempDir)

        val projectDir = tempDir.walk().find { it.name == RECORDER_MANIFEST }!!.parentFile
        val rcDir = outputDir.resolve(inputFile.nameWithoutExtension + "_converted")
            .apply {
                deleteRecursively()
                mkdirs()
            }

        // build manifest
        val mapper = ObjectMapper(JsonFactory()).registerKotlinModule()
        val manifestFile = projectDir.resolve(RECORDER_MANIFEST)
        val recorderManifest = mapper.readValue<Manifest>(manifestFile)
        val manifest = buildManifest(recorderManifest, rcDir)
        writeManifest(manifest, rcDir)

        val recorderSelectedTakes: List<String> = mapper.readValue(projectDir.resolve(RECORDER_SELECTED_FILE))
        val oratureSelectedTakes = mutableListOf<String>()

        val chapterDigitFormat = if (recorderManifest.chapters.size <= 99) "%02d" else "%03d"
        /* handle recorded takes */
        recorderManifest.chapters.forEach { chapter ->
            // create chapter folder
            val chapterPathName = String.format(chapterDigitFormat, chapter.chapterNumber)
            val chapterDirInRC = rcDir.resolve("$TAKE_DIR/c${chapterPathName}")
            chapterDirInRC.mkdir()

            val takesToCompile = mutableListOf<File>()
            // copy take files over
            chapter.chunks
                .sortedBy { it.start }
                .forEach { chunk ->
                    chunk.takes
                        .forEach { take ->
                            val takeFile = projectDir.resolve(chapterPathName).resolve(take.name)
                            val rcTakeName = buildOratureTakeName(
                                recorderManifest,
                                chapterPathName,
                                chunk.start,
                                "_t(\\d+)".toRegex().find(take.name)?.groupValues?.get(1)?.toInt() ?: 1
                            )
                            val takeFileInRC = chapterDirInRC.resolve(rcTakeName)
                            takeFile.copyTo(takeFileInRC)

                            if (take.name in recorderSelectedTakes) {
                                oratureSelectedTakes.add("c$chapterPathName/$rcTakeName")
                                takesToCompile.add(takeFileInRC)
                            }
                        }
                }

            if (chapterDirInRC.list().isEmpty()) {
                chapterDirInRC.delete()
            } else {
                val chapterTake = compileChapter(
                    recorderManifest,
                    chapterPathName,
                    chapterDirInRC,
                    takesToCompile
                )

                if (recorderManifest.mode.slug == "chunk") { // de-chunk to verses
                    val verseFiles = splitAudioOnMarkers(chapterTake, chapterDirInRC)
                    verseFiles.forEach { v ->
                        oratureSelectedTakes.add("c$chapterPathName/${v.name}")
                    }
                }

                val chapterCompleted = chapterHasAllVerses(
                    recorderManifest,
                    chapter.chapterNumber,
                    chapterDirInRC
                )
                if (chapterCompleted) {
                    oratureSelectedTakes.add("c$chapterPathName/${chapterTake.name}")
                } else {
                    chapterTake.delete()
                }
            }

        }

        val selectedTakesFile = rcDir.resolve(ORATURE_SELECTED_TAKES_FILE)
            .apply { createNewFile() } // resembles ongoing project
        selectedTakesFile.writeText(oratureSelectedTakes.joinToString("\n"))

        val zippedFile = outputDir.resolve("${rcDir.name}.zip").apply { delete() }
        zipDirectory(rcDir, zippedFile)

        tempDir.deleteRecursively()
        rcDir.deleteRecursively()

        return zippedFile
    }

    private fun compileChapter(
        recorderManifest: Manifest,
        chapterPathName: String,
        chapterDirInRC: File,
        takesToCompile: MutableList<File>
    ): File {
        val chapterTakeName = buildOratureTakeName(recorderManifest, chapterPathName, null, 1)
        val chapterTake = chapterDirInRC.resolve(chapterTakeName)
        chapterTake.createNewFile()
        concatAudio(takesToCompile, chapterTake)
        return chapterTake
    }

    private fun buildManifest(recorderManifest: Manifest, rcPath: File): RCManifest {
        val sourceDir = rcPath.resolve(SOURCE_DIR).apply { mkdirs() }
        val sourceLanguage = recorderManifest.sourceLanguage?.slug ?: "en" // default to English if empty
        val sourceFile = SourceBuilder.createMatchingSourceForLanguage(sourceLanguage, sourceDir)
            ?: throw UnsupportedRCException("Could not find matching source for '$sourceLanguage'.")

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
        mapper.writeValue(outDir.resolve(ORATURE_MANIFEST), manifest)
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
        recorderManifest: Manifest,
        chapter: String,
        verse: Int? = null,
        take: Int,
        extension: String = "wav"
    ): String {
        val language = recorderManifest.language.slug
        val resource = recorderManifest.version.slug
        val book = recorderManifest.book.slug
        return "${language}_${resource}_${book}_c${chapter}_${verse?.let { "v$verse" } ?: "meta"}_t$take.$extension"
    }

    private fun chapterHasAllVerses(manifest: Manifest, chapter: Int, chapterDir: File): Boolean {
        val book = manifest.book.slug
        val verseCount = versification[book.uppercase()]!![chapter - 1].toInt()
        val fileNamesInChapterDir = chapterDir.listFiles().map { it.name }
        for (i in 1 .. verseCount) {
            if (fileNamesInChapterDir.none { it.contains("_v${i}_t") }) {
                return false
            }
        }
        return true
    }
}