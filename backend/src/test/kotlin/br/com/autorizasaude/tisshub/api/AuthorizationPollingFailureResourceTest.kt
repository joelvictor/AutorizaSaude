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
    fun `should schedule retry when polling throws`() {
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
            .body("status", equalTo("PENDING_OPERATOR"))

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/authorizations/$authorizationId/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("PENDING_OPERATOR"))
            .body("dispatch.technicalStatus", equalTo("TECHNICAL_ERROR"))
            .body("dispatch.attemptCount", equalTo(2))
            .body("timeline[5].eventType", equalTo("EVT-013"))
            .body("timeline[6].eventType", equalTo("EVT-012"))
    }

    @Test
    fun `should move dispatch to dlq and fail authorization after max polling failures`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-POLL-DLQ",
                  "operatorCode": "UNIMED_ANAPOLIS",
                  "procedureCodes": ["12345679"],
                  "clinicalJustification": "Teste de exaustao de tentativas"
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
            .body("status", equalTo("PENDING_OPERATOR"))

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .`when`().post("/v1/authorizations/$authorizationId/poll")
            .then()
            .statusCode(200)
            .body("status", equalTo("PENDING_OPERATOR"))

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .`when`().post("/v1/authorizations/$authorizationId/poll")
            .then()
            .statusCode(200)
            .body("status", equalTo("PENDING_OPERATOR"))

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
            .body("dispatch.technicalStatus", equalTo("DLQ"))
            .body("dispatch.attemptCount", equalTo(5))
            .body("timeline[11].eventType", equalTo("EVT-013"))
            .body("timeline[12].eventType", equalTo("EVT-014"))
    }
}
