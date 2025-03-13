package org.bibletranslationtools.recorder2rc

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

fun unzipFile(file: File, destinationDir: File) {
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

fun zipDirectory(sourceDir: File, zipFile: File) {
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