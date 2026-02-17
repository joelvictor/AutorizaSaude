package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTestProfile

class PollFailureTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> {
        return mapOf(
            "quarkus.scheduler.enabled" to "false",
            "tisshub.operator.type-b.poll-failure-operators" to "UNIMED_ANAPOLIS"
        )
    }
}
