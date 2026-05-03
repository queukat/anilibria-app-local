package ru.radiationx.data.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientRetryPolicyTest {
    @Test
    fun retriesEnabledForIdempotentMethodsOnly() {
        assertTrue(RetryPolicy.shouldRetryOnException(Client.METHOD_GET, attempt = 0))
        assertTrue(RetryPolicy.shouldRetryOnException(Client.METHOD_HEAD, attempt = 0))
        assertFalse(RetryPolicy.shouldRetryOnException(Client.METHOD_POST, attempt = 0))
        assertFalse(RetryPolicy.shouldRetryOnException(Client.METHOD_PUT, attempt = 0))
        assertFalse(RetryPolicy.shouldRetryOnException(Client.METHOD_DELETE, attempt = 0))
    }

    @Test
    fun retriesForHttpCodeOnlyOn5xxAndWithinRetryBudget() {
        assertTrue(RetryPolicy.shouldRetryOnHttpCode(Client.METHOD_GET, code = 503, attempt = 0))
        assertTrue(RetryPolicy.shouldRetryOnHttpCode(Client.METHOD_GET, code = 500, attempt = 1))
        assertFalse(RetryPolicy.shouldRetryOnHttpCode(Client.METHOD_GET, code = 404, attempt = 0))
        assertFalse(RetryPolicy.shouldRetryOnHttpCode(Client.METHOD_GET, code = 503, attempt = 2))
        assertFalse(RetryPolicy.shouldRetryOnHttpCode(Client.METHOD_POST, code = 503, attempt = 0))
    }
}
