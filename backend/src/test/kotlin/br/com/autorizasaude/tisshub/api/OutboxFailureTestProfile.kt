package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTestProfile

class OutboxFailureTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> {
        return mapOf(
            "quarkus.scheduler.enabled" to "false",
            "tisshub.outbox.fail-event-types" to "EVT-002",
            "tisshub.outbox.retry-delays-seconds" to "0,0"
        )
    }
}
