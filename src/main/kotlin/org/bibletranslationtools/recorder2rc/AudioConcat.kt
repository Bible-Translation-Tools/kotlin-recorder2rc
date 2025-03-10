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
