package com.wander.android.data.sources.archive

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ArchiveSearchResponse(
    val response: ArchiveSearchResponseHeader? = null
)

@Serializable
data class ArchiveSearchResponseHeader(
    val numFound: Int = 0,
    val docs: List<ArchiveDoc>? = null
)

@Serializable
data class ArchiveDoc(
    val identifier: String,
    val title: String? = null,
    val creator: String? = null,
    val year: String? = null,
    val description: String? = null,
    val mediatype: String? = null,
    val format: List<String>? = null
)

@Serializable
data class ArchiveMetadataResponse(
    val server: String? = null,
    val dir: String? = null,
    val metadata: ArchiveMetadata? = null,
    val files: List<ArchiveFile>? = null
)

@Serializable
data class ArchiveMetadata(
    val identifier: String,
    val title: String? = null,
    val creator: String? = null,
    val year: String? = null,
    val date: String? = null,
    val description: String? = null
)

@Serializable
data class ArchiveFile(
    val name: String,
    val format: String? = null,
    val title: String? = null,
    val creator: String? = null,
    val album: String? = null,
    val length: String? = null,
    val track: String? = null,
    val size: String? = null,
    val bitrate: String? = null
)

@Singleton
class ArchiveApiClient @Inject constructor(
    private val client: HttpClient
) {
    private val searchBaseUrl = "https://archive.org/advancedsearch.php"
    private val metadataBaseUrl = "https://archive.org/metadata"

    suspend fun searchAudio(query: String, limit: Int = 30): Result<List<ArchiveDoc>> = withContext(Dispatchers.IO) {
        try {
            val q = "mediatype:audio AND ($query)"
            val response: ArchiveSearchResponse = client.get(searchBaseUrl) {
                parameter("q", q)
                parameter("fl[]", "identifier,title,creator,year,mediatype,description,format")
                parameter("rows", limit)
                parameter("page", 1)
                parameter("output", "json")
            }.body()
            Result.success(response.response?.docs ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCollectionAudio(collection: String = "etree", limit: Int = 30): Result<List<ArchiveDoc>> = withContext(Dispatchers.IO) {
        try {
            val q = "collection:$collection AND mediatype:audio"
            val response: ArchiveSearchResponse = client.get(searchBaseUrl) {
                parameter("q", q)
                parameter("fl[]", "identifier,title,creator,year,mediatype,description,format")
                parameter("sort[]", "downloads desc")
                parameter("rows", limit)
                parameter("page", 1)
                parameter("output", "json")
            }.body()
            Result.success(response.response?.docs ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMetadata(identifier: String): Result<ArchiveMetadataResponse> = withContext(Dispatchers.IO) {
        try {
            val response: ArchiveMetadataResponse = client.get("$metadataBaseUrl/$identifier").body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildDirectAudioUrl(server: String, dir: String, fileName: String): String {
        return "https://$server$dir/$fileName"
    }

    fun buildCoverArtUrl(identifier: String): String {
        return "https://archive.org/services/img/$identifier"
    }
}
