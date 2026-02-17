package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AuthorizationResourceTest {
    @Test
    fun `should create authorization`() {
        given()
            .header("X-Tenant-Id", UUID.randomUUID().toString())
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-001",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["12345678"],
                  "clinicalJustification": "Indicacao medica"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .body("authorizationId", notNullValue())
            .body("status", equalTo("DRAFT"))
    }

    @Test
    fun `should replay with same idempotency key`() {
        val tenantId = UUID.randomUUID().toString()
        val idempotencyKey = "idem-${UUID.randomUUID()}"
        val payload = """
            {
              "patientId": "P-002",
              "operatorCode": "AMIL",
              "procedureCodes": ["87654321"],
              "clinicalJustification": "Justificativa clinica"
            }
        """.trimIndent()

        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", idempotencyKey)
            .contentType("application/json")
            .body(payload)
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .extract()
            .path<String>("authorizationId")

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", idempotencyKey)
            .contentType("application/json")
            .body(payload)
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(200)
            .body("authorizationId", equalTo(authorizationId))
            .body("status", equalTo("DRAFT"))
    }

    @Test
    fun `should return conflict when idempotency key payload changes`() {
        val tenantId = UUID.randomUUID().toString()
        val idempotencyKey = "idem-${UUID.randomUUID()}"

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", idempotencyKey)
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-003",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["11111111"],
                  "clinicalJustification": "Primeira solicitacao"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", idempotencyKey)
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-003",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["22222222"],
                  "clinicalJustification": "Solicitacao alterada"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(409)
    }

    @Test
    fun `should isolate tenant access`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-004",
                  "operatorCode": "SULAMERICA",
                  "procedureCodes": ["33333333"],
                  "clinicalJustification": "Isolamento por tenant"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .extract()
            .path<String>("authorizationId")

        given()
            .header("X-Tenant-Id", UUID.randomUUID().toString())
            .`when`().get("/v1/authorizations/$authorizationId")
            .then()
            .statusCode(404)
    }

    @Test
    fun `should cancel authorization`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-005",
                  "operatorCode": "PORTO",
                  "procedureCodes": ["44444444"],
                  "clinicalJustification": "Pedido de cancelamento"
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
            .contentType("application/json")
            .body("""{"reason":"Solicitado pelo prestador"}""")
            .`when`().post("/v1/authorizations/$authorizationId/cancel")
            .then()
            .statusCode(200)
            .body("status", equalTo("CANCELLED"))
    }
}
