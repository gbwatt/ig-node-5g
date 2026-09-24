package com.igsave.node

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class InstagramResolver(private val client: OkHttpClient) {

    companion object {
        fun createOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    fun resolve(url: String): JsonObject? {
        val shortcode = extractShortcode(url) ?: return null

        // 1. Direct Web Info API
        val directUrl = "https://www.instagram.com/p/$shortcode/?__a=1&__d=dis"
        val request = Request.Builder()
            .url(directUrl)
            .header("User-Agent", "Instagram 319.0.0.0 (iPhone14,2; iOS 16_6; en_US)")
            .header("X-IG-App-ID", "936619743392459")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Sec-Fetch-Site", "same-origin")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JsonParser.parseString(body).asJsonObject
                    val item = when {
                        json.has("graphql") && json.getAsJsonObject("graphql").has("shortcode_media") ->
                            json.getAsJsonObject("graphql").getAsJsonObject("shortcode_media")
                        json.has("items") && json.getAsJsonArray("items").size() > 0 ->
                            json.getAsJsonArray("items").get(0).asJsonObject
                        else -> null
                    }
                    if (item != null) {
                        return formatItem(item, shortcode)
                    }
                }
            }
        } catch (e: Exception) {}

        // 2. GraphQL Query Fallback
        val gqlUrl = "https://www.instagram.com/graphql/query/?query_hash=b3055c2e4705d69612121d0a0f1dee5a&variables=%7B%22shortcode%22%3A%22$shortcode%22%7D"
        val gqlReq = Request.Builder()
            .url(gqlUrl)
            .header("User-Agent", "Instagram 319.0.0.0 (iPhone14,2; iOS 16_6; en_US)")
            .header("X-IG-App-ID", "936619743392459")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        try {
            client.newCall(gqlReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JsonParser.parseString(body).asJsonObject
                    val media = json.getAsJsonObject("data")?.getAsJsonObject("shortcode_media")
                    if (media != null) {
                        return formatItem(media, shortcode)
                    }
                }
            }
        } catch (e: Exception) {}

        return null
    }

    private fun formatItem(item: JsonObject, shortcode: String): JsonObject {
        val root = JsonObject()
        val itemsArray = JsonArray()

        // Check Carousel
        val carousel = when {
            item.has("edge_sidecar_to_children") && item.getAsJsonObject("edge_sidecar_to_children").has("edges") ->
                item.getAsJsonObject("edge_sidecar_to_children").getAsJsonArray("edges")
            item.has("carousel_media") && item.get("carousel_media").isJsonArray ->
                item.getAsJsonArray("carousel_media")
            else -> null
        }

        if (carousel != null && carousel.size() > 0) {
            for (elem in carousel) {
                val node = if (elem.isJsonObject && elem.asJsonObject.has("node")) {
                    elem.asJsonObject.getAsJsonObject("node")
                } else {
                    elem.asJsonObject
                }
                val isVid = node.has("is_video") && node.get("is_video").asBoolean ||
                        node.has("video_url") ||
                        (node.has("media_type") && node.get("media_type").asInt == 2)

                val mediaUrl = when {
                    node.has("video_url") -> node.get("video_url").asString
                    node.has("video_versions") && node.getAsJsonArray("video_versions").size() > 0 ->
                        node.getAsJsonArray("video_versions").get(0).asJsonObject.get("url").asString
                    node.has("display_url") -> node.get("display_url").asString
                    node.has("image_versions2") ->
                        node.getAsJsonObject("image_versions2").getAsJsonArray("candidates").get(0).asJsonObject.get("url").asString
                    else -> null
                }

                if (!mediaUrl.isNullOrEmpty()) {
                    val mediaObj = JsonObject()
                    mediaObj.addProperty("type", if (isVid) "video" else "photo")
                    mediaObj.addProperty("download_url", mediaUrl)
                    mediaObj.addProperty("direct_url", mediaUrl)
                    itemsArray.add(mediaObj)
                }
            }
        } else {
            // Single Photo / Video
            val isVid = item.has("is_video") && item.get("is_video").asBoolean ||
                    item.has("video_url") ||
                    (item.has("media_type") && item.get("media_type").asInt == 2)

            val mediaUrl = when {
                item.has("video_url") -> item.get("video_url").asString
                item.has("video_versions") && item.getAsJsonArray("video_versions").size() > 0 ->
                    item.getAsJsonArray("video_versions").get(0).asJsonObject.get("url").asString
                item.has("display_url") -> item.get("display_url").asString
                item.has("image_versions2") ->
                    item.getAsJsonObject("image_versions2").getAsJsonArray("candidates").get(0).asJsonObject.get("url").asString
                else -> null
            }

            if (!mediaUrl.isNullOrEmpty()) {
                val mediaObj = JsonObject()
                mediaObj.addProperty("type", if (isVid) "video" else "photo")
                mediaObj.addProperty("download_url", mediaUrl)
                mediaObj.addProperty("direct_url", mediaUrl)
                itemsArray.add(mediaObj)
            }
        }

        root.addProperty("success", itemsArray.size() > 0)
        root.addProperty("shortcode", shortcode)
        root.add("items", itemsArray)
        return root
    }

    private fun extractShortcode(url: String): String? {
        val regex = Regex("""/(?:reel|reels|p)/([A-Za-z0-9_-]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }
}
