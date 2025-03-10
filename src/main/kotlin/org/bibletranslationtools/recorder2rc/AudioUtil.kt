package org.bibletranslationtools.recorder2rc

import org.wycliffeassociates.otter.common.audio.AudioFile
import org.wycliffeassociates.otter.common.audio.wav.CueChunk
import org.wycliffeassociates.otter.common.audio.wav.WavMetadata
import java.io.File

fun concatAudio(files: List<File>, outputFile: File) {
    val outputAudioFile = AudioFile(outputFile, 1, 41000, 16, WavMetadata(listOf(CueChunk())))

    outputAudioFile.writer(append = true).use { writeStream ->
        files.forEach { f ->
            val audioFile = AudioFile(f)
            audioFile.reader().use { reader ->
                reader.open()
                while (reader.hasRemaining()) {
                    val buffer = ByteArray(10240)
                    val written = reader.getPcmBuffer(buffer)
                    writeStream.write(buffer, 0, written)
                }
            }
        }
    }

    // join markers
    var currentFrame = 0
    files.forEach { f ->
        val audioFile = AudioFile(f)
        audioFile.getCues().forEach { cue ->
            outputAudioFile.addCue(currentFrame + cue.location, cue.label)
        }
        currentFrame += audioFile.totalFrames
    }
    outputAudioFile.update()
}

fun splitAudioOnMarkers(chapterFile: File, outputDir: File): List<File> {
    val chapterAudio = AudioFile(chapterFile)
    val cues = chapterAudio.getCues().sortedBy { it.location }
    val verseFiles = mutableListOf<File>()

    cues.forEachIndexed { index, c ->
        val start = c.location
        val end = if (index < cues.lastIndex) {
            cues[index + 1].location
        } else {
            null
        }

        val verseFileName = chapterFile.name.replaceFirst("_meta", "_v${c.label}")
        val verseFile = outputDir.resolve(verseFileName)
            .apply { createNewFile() }
        val verseAudio = AudioFile(verseFile, 1, 41000, 16, WavMetadata(listOf(CueChunk())))

        chapterAudio.reader(start = start, end = end).use { reader ->
            verseAudio.writer(append = false).use { writer ->
                reader.open()
                val buffer = ByteArray(10240)
                while (reader.hasRemaining()) {
                    val written = reader.getPcmBuffer(buffer)
                    writer.write(buffer, 0, written)
                }
            }
        }

        verseAudio.addCue(location = 0, c.label)
        verseAudio.update()
        verseFiles.add(verseFile)
    }

    return verseFiles
}