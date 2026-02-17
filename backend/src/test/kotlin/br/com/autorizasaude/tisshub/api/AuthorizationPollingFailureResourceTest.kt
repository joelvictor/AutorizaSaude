package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(PollFailureTestProfile::class)
class AuthorizationPollingFailureResourceTest {
    @Test
    fun `should mark dispatch and authorization as technical failure when polling throws`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-POLL-ERR",
                  "operatorCode": "UNIMED_ANAPOLIS",
                  "procedureCodes": ["12345678"],
                  "clinicalJustification": "Teste de falha no polling"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .body("status", equalTo("PENDING_OPERATOR"))
            .extract()
            .path<String>("authorizationId")

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .`when`().post("/v1/authorizations/$authorizationId/poll")
            .then()
            .statusCode(200)
            .body("status", equalTo("FAILED_TECHNICAL"))

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/authorizations/$authorizationId/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("FAILED_TECHNICAL"))
            .body("dispatch.technicalStatus", equalTo("TECHNICAL_ERROR"))
            .body("dispatch.attemptCount", equalTo(2))
            .body("timeline[5].eventType", equalTo("EVT-013"))
    }
}
