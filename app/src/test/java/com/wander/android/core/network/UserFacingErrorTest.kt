package com.wander.android.core.network

import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.navidrome.SubsonicServerException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFacingErrorTest {

    @Test
    fun `transport failures are network errors`() {
        assertEquals(ErrorKind.NETWORK, UnknownHostException("h").errorKind())
        assertEquals(ErrorKind.NETWORK, SocketTimeoutException("t").errorKind())
        assertEquals(ErrorKind.NETWORK, AgroAuthError.Unreachable("u").errorKind())
    }

    @Test
    fun `refused credentials are auth errors`() {
        assertEquals(ErrorKind.AUTH, AgroAuthError.Rejected("r").errorKind())
        assertEquals(ErrorKind.AUTH, AgroAuthError.NotActive("n").errorKind())
        assertEquals(ErrorKind.AUTH, SubsonicServerException(40, "wrong").errorKind())
    }

    @Test
    fun `an answer we cannot use is a server error`() {
        assertEquals(ErrorKind.SERVER, SubsonicServerException(70, "not found").errorKind())
        assertEquals(ErrorKind.SERVER, AgroAuthError.RateLimited("slow down").errorKind())
        assertEquals(ErrorKind.SERVER, IOException("Agro returned no data").errorKind())
    }

    @Test
    fun `anything else is unknown`() {
        assertEquals(ErrorKind.UNKNOWN, IllegalStateException("x").errorKind())
    }
}
