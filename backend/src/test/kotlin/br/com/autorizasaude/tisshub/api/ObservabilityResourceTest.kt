package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class ObservabilityResourceTest {
    @Test
    fun `should return zeroed observability summary for empty tenant`() {
        given()
            .header("X-Tenant-Id", UUID.randomUUID().toString())
            .`when`().get("/v1/operations/observability/summary")
            .then()
            .statusCode(200)
            .body("latencyP95ByOperator.size()", equalTo(0))
            .body("successRateByOperatorAndType.size()", equalTo(0))
            .body("queues.dispatchRetryQueue", equalTo(0))
            .body("queues.dispatchDeadLetter", equalTo(0))
            .body("queues.outboxRetryQueue", equalTo(0))
            .body("queues.outboxDeadLetter", equalTo(0))
    }

    @Test
    fun `should expose operator latency and success metrics`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-OBS",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["91919191"],
                  "clinicalJustification": "Teste observabilidade"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .extract()
            .path<String>("authorizationId")

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .`when`().post("/v1/authorizations/$authorizationId/poll")
            .then()
            .statusCode(200)

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/operations/observability/summary")
            .then()
            .statusCode(200)
            .body("latencyP95ByOperator.operatorCode", hasItem("BRADESCO"))
            .body("successRateByOperatorAndType.operatorCode", hasItem("BRADESCO"))
            .body("successRateByOperatorAndType.dispatchType", hasItem("TYPE_A"))
    }
}
