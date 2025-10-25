// FILE: app/src/main/java/com/example/tiendamascotas/assistant/AssistantService.kt  (NUEVO)
package com.example.tiendamascotas.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface AssistantService {
    suspend fun chat(req: AssistantChatRequest): Result<AssistantChatResponse>
}

class OkHttpAssistantService(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : AssistantService {

    override suspend fun chat(req: AssistantChatRequest): Result<AssistantChatResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = baseUrl.trimEnd('/') + "/chat"
                val json = JSONObject().apply {
                    put("userId", req.userId)
                    put("message", req.message)
                    put("species", req.species)
                    put("locale", req.locale)
                }
                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val httpReq = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(httpReq).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val txt = resp.body?.string().orEmpty()
                    val obj = JSONObject(txt)

                    val reply = obj.optString("reply")
                    val risk = obj.optString("risk", "none")
                    val sourcesArr = obj.optJSONArray("sources") ?: JSONArray()
                    val sources = buildList {
                        for (i in 0 until sourcesArr.length()) add(sourcesArr.optString(i))
                    }
                    AssistantChatResponse(reply, sources, risk)
                }
            }
        }
}
