import org.wycliffeassociates.otter.common.audio.AudioFile
import org.wycliffeassociates.recorder2rc.Recorder2RCConverter
import org.wycliffeassociates.resourcecontainer.ResourceContainer
import org.wycliffeassociates.resourcecontainer.entity.DublinCore
import org.wycliffeassociates.resourcecontainer.entity.Language
import org.wycliffeassociates.resourcecontainer.entity.Source
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConverterTest {

    private lateinit var tempDir: File
    private lateinit var inputFile: File

    @BeforeTest
    fun setup(): Unit {
        tempDir = createTempDirectory("converter-test").toFile()
        inputFile = tempDir.resolve("recorder-project.zip")
        javaClass.classLoader.getResourceAsStream("recorder/vi_ot_ulb_psa.zip")!!.use { read ->
            inputFile.outputStream().use { write -> read.transferTo(write) }
        }
    }

    @AfterTest
    fun cleanUp() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testConvertTranslationProject() {
        val outputFile = Recorder2RCConverter().convert(inputFile, tempDir)
        val metadata = DublinCore(
            type = "book",
            conformsTo = "rc0.2",
            format = "audio/wav",
            identifier = "ulb",
            title = "unlocked literal bible",
            subject = "Bible",
            description = "",
            language = Language(identifier = "vi", title = "Tiếng Việt", direction = ""),
            source = mutableListOf(Source(identifier = "ulb", language = "vi", version = "6.6")),
            rights = "CC BY-SA 4.0",
            creator = "Orature",
            contributor = mutableListOf(),
            relation = mutableListOf(),
            publisher = "Wycliffe Associates",
            issued = "2025-03-05",
            modified = "2025-03-05",
            version = "1"
        )

        val takeList = listOf(
            ".apps/orature/takes/c001/vi_ulb_psa_c001_meta_t1.wav",
            ".apps/orature/takes/c001/vi_ulb_psa_c001_v1_t2.wav",
            ".apps/orature/takes/c001/vi_ulb_psa_c001_v2_t1.wav",
            ".apps/orature/takes/c001/vi_ulb_psa_c001_v3_t1.wav",
            ".apps/orature/takes/c001/vi_ulb_psa_c001_v4_t3.wav",
            ".apps/orature/takes/c001/vi_ulb_psa_c001_v5_t1.wav",
            ".apps/orature/takes/c001/vi_ulb_psa_c001_v6_t1.wav",
            ".apps/orature/takes/c002/vi_ulb_psa_c002_meta_t1.wav",
            ".apps/orature/takes/c002/vi_ulb_psa_c002_v1_t1.wav",
            ".apps/orature/takes/c002/vi_ulb_psa_c002_v2_t1.wav"
        )

        ResourceContainer.load(outputFile).use {
            assertEquals(metadata, it.manifest.dublinCore)
            val filesInRC = it.accessor.list("./")

            takeList.forEach { take ->
                assertTrue(take in filesInRC, "Output RC must have all takes.")
            }

            it.accessor.getInputStream(".apps/orature/takes/c001/vi_ulb_psa_c001_meta_t1.wav").use { input ->
                val firstChapterFile = kotlin.io.path.createTempFile(tempDir.toPath(), "chapter-take", ".wav").toFile()
                firstChapterFile.outputStream().use { out -> input.transferTo(out) }

                val markers = AudioFile(firstChapterFile).metadata.getCues()
                assertEquals(6, markers.size)
            }
        }
    }
}