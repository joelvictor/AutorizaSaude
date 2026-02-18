package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class OutboxOperationsResourceTest {
    @Test
    fun `should process pending outbox events`() {
        // Drain any leftovers from previous tests in same JVM.
        given()
            .`when`().post("/v1/operations/outbox/process")
            .then()
            .statusCode(200)

        given()
            .`when`().get("/v1/operations/outbox")
            .then()
            .statusCode(200)
            .body("pending", greaterThanOrEqualTo(0))

        given()
            .header("X-Tenant-Id", UUID.randomUUID().toString())
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-OUTBOX",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["99999999"],
                  "clinicalJustification": "Teste outbox relay"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .body("status", equalTo("DISPATCHED"))

        given()
            .`when`().get("/v1/operations/outbox")
            .then()
            .statusCode(200)
            .body("pending", greaterThanOrEqualTo(12))

        given()
            .`when`().post("/v1/operations/outbox/process")
            .then()
            .statusCode(200)
            .body("scanned", greaterThanOrEqualTo(12))
            .body("published", greaterThanOrEqualTo(12))
            .body("failed", equalTo(0))
            .body("deadLettered", equalTo(0))

        given()
            .`when`().get("/v1/operations/outbox")
            .then()
            .statusCode(200)
            .body("pending", equalTo(0))
            .body("published", greaterThanOrEqualTo(12))
    }
}
