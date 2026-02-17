package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
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
            .body("status", equalTo("DISPATCHED"))
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
            .body("status", equalTo("DISPATCHED"))
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

    @Test
    fun `should return consolidated status with timeline and dispatch`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-006",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["55555555"],
                  "clinicalJustification": "Status consolidado"
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
            .`when`().get("/v1/authorizations/$authorizationId/status")
            .then()
            .statusCode(200)
            .body("authorizationId", equalTo(authorizationId))
            .body("status", equalTo("DISPATCHED"))
            .body("timeline.size()", equalTo(4))
            .body("timeline[0].eventType", equalTo("EVT-001"))
            .body("timeline[1].eventType", equalTo("EVT-002"))
            .body("timeline[2].eventType", equalTo("EVT-003"))
            .body("timeline[3].eventType", equalTo("EVT-005"))
            .body("dispatch.dispatchType", equalTo("TYPE_A"))
            .body("dispatch.technicalStatus", equalTo("READY"))
    }

    @Test
    fun `should fail technical when tiss xml is invalid`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-007",
                  "operatorCode": "OPERADORA INVALIDA",
                  "procedureCodes": ["66666666"],
                  "clinicalJustification": "Deve falhar no XSD"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .body("status", equalTo("FAILED_TECHNICAL"))
            .extract()
            .path<String>("authorizationId")

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/authorizations/$authorizationId/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("FAILED_TECHNICAL"))
            .body("timeline[2].eventType", equalTo("EVT-004"))
            .body("dispatch", nullValue())
    }
}
