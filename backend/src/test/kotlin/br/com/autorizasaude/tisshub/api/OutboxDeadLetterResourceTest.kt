package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
@TestProfile(OutboxFailureTestProfile::class)
class OutboxDeadLetterResourceTest {
    @Test
    fun `should move event to dead letter after max retries`() {
        given()
            .`when`().post("/v1/operations/outbox/process")
            .then()
            .statusCode(200)

        given()
            .header("X-Tenant-Id", UUID.randomUUID().toString())
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-DLQ",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["12312312"],
                  "clinicalJustification": "Teste DLQ"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)

        // EVT-002 is configured to fail in this profile.
        given().`when`().post("/v1/operations/outbox/process").then().statusCode(200)
        given().`when`().post("/v1/operations/outbox/process").then().statusCode(200)
        given().`when`().post("/v1/operations/outbox/process").then().statusCode(200)

        given()
            .`when`().get("/v1/operations/outbox")
            .then()
            .statusCode(200)
            .body("deadLetter", greaterThanOrEqualTo(1))
    }

    @Test
    fun `should list and requeue dead letters by tenant`() {
        val tenantId = UUID.randomUUID().toString()

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-DLQ-REQUEUE",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["32132132"],
                  "clinicalJustification": "Teste requeue"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)

        // EVT-002 is configured to fail in this profile and move to dead letter after 3 attempts.
        given().`when`().post("/v1/operations/outbox/process").then().statusCode(200)
        given().`when`().post("/v1/operations/outbox/process").then().statusCode(200)
        given().`when`().post("/v1/operations/outbox/process").then().statusCode(200)

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/operations/outbox/dead-letters")
            .then()
            .statusCode(200)
            .body("deadLetters.size()", equalTo(1))
            .body("deadLetters[0].eventType", equalTo("EVT-002"))

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .`when`().post("/v1/operations/outbox/dead-letters/requeue")
            .then()
            .statusCode(200)
            .body("requeued", equalTo(1))

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/operations/outbox/dead-letters")
            .then()
            .statusCode(200)
            .body("deadLetters.size()", equalTo(0))

        given()
            .`when`().get("/v1/operations/outbox")
            .then()
            .statusCode(200)
            .body("pending", greaterThan(0))
    }
}
