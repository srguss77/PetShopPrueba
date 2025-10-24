package com.example.tiendamascotas.map

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OverpassService {
    // radius en metros (ej. 3000 = 3 km)
    fun nearbyVeterinaries(
        lat: Double,
        lng: Double,
        radius: Int = 3000
    ): List<VetPlace> {
        // Overpass QL: veterinarias (amenity=veterinary) y tiendas de mascotas (shop=pet)
        val query = """
            [out:json][timeout:25];
            (
              node(around:$radius,$lat,$lng)["amenity"="veterinary"];
              way(around:$radius,$lat,$lng)["amenity"="veterinary"];
              node(around:$radius,$lat,$lng)["shop"="pet"];
            );
            out center 50;
        """.trimIndent()

        val data = "data=" + URLEncoder.encode(query, "UTF-8")
        val url = URL("https://overpass-api.de/api/interpreter")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(data.toByteArray()) }

        return conn.inputStream.use { input ->
            val body = input.bufferedReader().readText()
            val json = JSONObject(body)
            val elements = json.optJSONArray("elements") ?: return emptyList()
            val items = mutableListOf<VetPlace>()

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val id = el.optLong("id", -1L).takeIf { it > 0 }?.toString() ?: continue

                val type = el.optString("type", "")
                val tags = el.optJSONObject("tags") ?: JSONObject()
                val name = tags.optString("name", "Veterinaria")
                val address = listOfNotNull(
                    tags.optString("addr:street", null),
                    tags.optString("addr:housenumber", null),
                    tags.optString("addr:city", null)
                ).joinToString(" ")

                val (latV, lonV) = when (type) {
                    "node" -> el.optDouble("lat") to el.optDouble("lon")
                    "way"  -> {
                        val center = el.optJSONObject("center")
                        center?.optDouble("lat") to center?.optDouble("lon")
                    }
                    else -> null to null
                }

                if (latV != null && lonV != null && !latV.isNaN() && !lonV.isNaN()) {
                    items += VetPlace(
                        id = id,
                        name = name,
                        lat = latV,
                        lng = lonV,
                        address = address.ifBlank { null },
                        rating = null // OSM no trae rating
                    )
                }
            }
            items
        }
    }
}

