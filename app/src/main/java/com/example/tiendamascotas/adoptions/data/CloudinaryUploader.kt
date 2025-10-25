package com.example.tiendamascotas.adoptions.data

import android.content.Context
import android.net.Uri
import com.example.tiendamascotas.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object CloudinaryUploader {

    private val client by lazy { OkHttpClient() }

    /**
     * Sube una imagen a Cloudinary (unsigned). Devuelve secure_url o lanza error con detalle.
     */
    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        folder: String = "adoptions"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cloud = BuildConfig.CLOUDINARY_CLOUD_NAME
            val preset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
            require(cloud.isNotBlank()) { "CLOUDINARY_CLOUD_NAME vacío" }
            require(preset.isNotBlank()) { "CLOUDINARY_UPLOAD_PRESET vacío" }

            val tmp = copyToCache(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    tmp.name,
                    tmp.asRequestBody(mime.toMediaType())
                )
                .addFormDataPart("upload_preset", preset)
                .addFormDataPart("folder", folder)
                .build()

            val req = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloud/image/upload")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()

                if (!resp.isSuccessful) {
                    val msg = try {
                        JSONObject(raw).optJSONObject("error")?.optString("message")
                    } catch (_: Exception) { null }
                    throw IOException("Cloudinary ${resp.code}: ${msg ?: raw.take(200)}")
                }

                val json = JSONObject(raw)
                json.getString("secure_url")
            }
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File {
        val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir la imagen")
        val outFile = File.createTempFile("upl_", ".img", context.cacheDir)
        FileOutputStream(outFile).use { out -> input.copyTo(out) }
        input.close()
        return outFile
    }
}
