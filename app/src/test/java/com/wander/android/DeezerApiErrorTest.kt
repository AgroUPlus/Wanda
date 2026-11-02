package com.wander.android

import com.wander.android.data.sources.deezer.DeezerApiClient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerApiErrorTest {

    @Test
    fun `a 200 carrying an error object is a failure`() {
        val root = Json.parseToJsonElement(
            """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""
        )
        val error = DeezerApiClient.apiError(root)
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("Quota limit exceeded"))
    }

    @Test
    fun `ordinary data is not an error`() {
        assertNull(DeezerApiClient.apiError(Json.parseToJsonElement("""{"data":[],"total":0}""")))
        assertNull(DeezerApiClient.apiError(Json.parseToJsonElement("""[1,2]""")))
    }
}
