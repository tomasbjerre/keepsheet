package com.github.tomasbjerre.keepsheet

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Real photographed pages of a public government brochure ("Om krisen eller kriget
 * kommer", Sweden's MSB — distributed to every household, no personal information),
 * used as realistic fixtures wherever a test or screenshot needs to look like an
 * actual scanned document rather than a generated placeholder (see keepsheet#55).
 * Bundled in this *test* APK's own assets (`src/androidTest/assets/sample-pages`),
 * not the app's — copied out to a real file since every consumer here needs a path.
 */
object SamplePages {
    const val COVER = "brochure-cover.jpg"
    const val PAGE_03 = "brochure-page-03.jpg"
    const val PAGE_04_CONTENTS = "brochure-page-04-contents.jpg"
    const val PAGE_05 = "brochure-page-05.jpg"
    const val PAGE_17_LANDSCAPE = "brochure-page-17-landscape.jpg"

    fun copyToCache(
        context: Context,
        assetName: String,
        destinationName: String = assetName,
    ): File {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val destination = File(context.cacheDir, destinationName)
        testAssets.open("sample-pages/$assetName").use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }
}
