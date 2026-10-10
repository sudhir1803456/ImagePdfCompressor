package com.example.compressor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val isPdf: Boolean,
    val originalSizeBytes: Long
)

data class CompressionResult(
    val fileName: String,
    val originalSize: Long,
    val compressedSize: Long,
    val savedUri: Uri
)

class CompressionEngine(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun compressFileToUri(
        file: SelectedFile,
        destinationUri: Uri,
        quality: Int
    ): CompressionResult = withContext(Dispatchers.IO) {
        val compressedBytes = if (file.isPdf) {
            compressPdfBytes(file, quality)
        } else {
            compressImageBytes(file, quality)
        }

        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
            outputStream.write(compressedBytes)
        }

        CompressionResult(
            fileName = file.name,
            originalSize = file.originalSizeBytes,
            compressedSize = compressedBytes.size.toLong(),
            savedUri = destinationUri
        )
    }

    private fun compressImageBytes(file: SelectedFile, quality: Int): ByteArray {
        val inputStream: InputStream? = context.contentResolver.openInputStream(file.uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val outputStream = ByteArrayOutputStream()
        val format = if (file.name.endsWith(".png", ignoreCase = true)) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }

        originalBitmap.compress(format, quality, outputStream)
        return outputStream.toByteArray()
    }

    private fun compressPdfBytes(file: SelectedFile, quality: Int): ByteArray {
        val inputStream: InputStream? = context.contentResolver.openInputStream(file.uri)
        val document = PDDocument.load(inputStream)
        val renderer = PDFRenderer(document)

        val newDocument = PDDocument()

        for (i in 0 until document.numberOfPages) {
            val scale = (quality / 100f).coerceAtLeast(0.3f)
            val bitmap = renderer.renderImage(i, scale)

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

            val page = PDPage()
            newDocument.addPage(page)

            val xImage = JPEGFactory.createFromStream(
                newDocument,
                baos.toByteArray().inputStream()
            )
            val contentStream = PDPageContentStream(newDocument, page)
            contentStream.drawImage(xImage, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
            contentStream.close()
        }

        val pdfOutputStream = ByteArrayOutputStream()
        newDocument.save(pdfOutputStream)
        document.close()
        newDocument.close()

        return pdfOutputStream.toByteArray()
    }
}