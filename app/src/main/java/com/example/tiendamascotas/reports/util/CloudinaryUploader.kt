package com.example.tiendamascotas.reports.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

suspend fun uploadToCloudinary(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext Result.failure(IllegalStateException("No se pudo leer la imagen"))

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "photo.jpg",
                bytes.toRequestBody("image/*".toMediaType())
            )
            .addFormDataPart("upload_preset", CloudinaryConfig.UNSIGNED_PRESET)
            .build()

        val req = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/${CloudinaryConfig.CLOUD_NAME}/upload")
            .post(body)
            .build()

        val resp = OkHttpClient().newCall(req).execute()
        if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
        val json = JSONObject(resp.body?.string() ?: "{}")
        val secureUrl = json.optString("secure_url")
        if (secureUrl.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Respuesta inválida de Cloudinary"))
        }
        Result.success(secureUrl)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
