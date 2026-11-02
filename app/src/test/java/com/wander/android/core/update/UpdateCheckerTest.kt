package com.wander.android.core.update

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Each way the lookup can fail is reported as itself, not folded into one "couldn't check". */
// Robolectric for android.util.Log; a bare Application, since nothing here needs the app's own startup.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateCheckerTest {

    private fun checker(engine: MockEngine) = UpdateChecker(HttpClient(engine))

    private fun answering(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        checker(MockEngine { respond(content = body, status = status) })

    @Test
    fun aNewerReleaseIsOffered() = runTest {
        val result = answering(
            """{"tag_name":"v999.0.0","html_url":"https://example.org/r","extra":1}"""
        ).checkForUpdate()
        assertEquals(UpdateCheckResult.UpdateAvailable("999.0.0", "https://example.org/r"), result)
    }

    @Test
    fun anOlderReleaseIsUpToDate() = runTest {
        val result = answering("""{"tag_name":"v0.0.1","html_url":"https://example.org/r"}""").checkForUpdate()
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun aRateLimitIsAnHttpFailure() = runTest {
        val result = answering("""{"message":"API rate limit exceeded"}""", HttpStatusCode.Forbidden).checkForUpdate()
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.Http(403)), result)
    }

    @Test
    fun anUnreadableReplyIsAParseFailure() = runTest {
        assertEquals(
            UpdateCheckResult.Failed(UpdateCheckFailure.Parse),
            answering("<html>maintenance</html>").checkForUpdate()
        )
        assertEquals(
            UpdateCheckResult.Failed(UpdateCheckFailure.Parse),
            answering("""{"tag_name":"embedder-v1","html_url":"https://example.org/r"}""").checkForUpdate()
        )
    }

    @Test
    fun noConnectionIsANetworkFailure() = runTest {
        val result = checker(MockEngine { throw IOException("unreachable") }).checkForUpdate()
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.Network), result)
    }
}
