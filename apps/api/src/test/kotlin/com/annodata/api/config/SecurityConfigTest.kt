package com.annodata.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SecurityConfigTest {
    @Test
    fun `reads and normalizes worker dispatch configuration`() {
        val config = SecurityConfig.from(
            mapOf(
                "AI_WORKER_BASE_URL" to " http://localhost:7100/ ",
                "WORKER_TRIGGER_TOKEN" to " trigger-token ",
            )
        )

        assertEquals("http://localhost:7100", config.aiWorkerBaseUrl)
        assertEquals("trigger-token", config.workerTriggerToken)
    }

    @Test
    fun `leaves missing worker dispatch configuration unset`() {
        val config = SecurityConfig.from(emptyMap())

        assertNull(config.aiWorkerBaseUrl)
        assertNull(config.workerTriggerToken)
    }
}
