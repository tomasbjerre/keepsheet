package com.github.tomasbjerre.keepsheet.ocr

import android.content.Context
import android.os.LocaleList
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * [TextRecognizer] backed by Tesseract (Tesseract4Android), fully on-device — nothing is
 * sent anywhere (specs/permissions-and-privacy.md). Trained data ships in the APK's
 * `assets/tessdata` and is copied once to app-private storage, since Tesseract needs real
 * files.
 */
class TesseractTextRecognizer(
    private val context: Context,
) : TextRecognizer {
    override fun recognize(imagePath: String): String? {
        val dataDir = ensureTrainedData()
        val api = TessBaseAPI()
        return try {
            if (!api.init(dataDir.absolutePath, tesseractLanguages(deviceLanguages()))) return null
            api.setImage(File(imagePath))
            api.utF8Text
        } finally {
            api.recycle()
        }
    }

    private fun deviceLanguages(): List<String> {
        val locales = LocaleList.getDefault()
        return (0 until locales.size()).map { locales[it].language }
    }

    @Synchronized
    private fun ensureTrainedData(): File {
        val root = File(context.filesDir, "ocr")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        bundledLanguageCodes().forEach { copyFromAssets(it, tessdata) }
        return root
    }

    private fun copyFromAssets(
        code: String,
        tessdata: File,
    ) {
        val target = File(tessdata, "$code.traineddata")
        if (target.exists()) return
        context.assets.open("tessdata/$code.traineddata").use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }
}
