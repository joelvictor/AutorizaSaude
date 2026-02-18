package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.not
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
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue())
            .body("operatorProtocol", notNullValue())
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
            .body("timeline.size()", equalTo(6))
            .body("timeline[0].eventType", equalTo("EVT-001"))
            .body("timeline[1].eventType", equalTo("EVT-002"))
            .body("timeline[2].eventType", equalTo("EVT-003"))
            .body("timeline[3].eventType", equalTo("EVT-005"))
            .body("timeline[4].eventType", equalTo("EVT-006"))
            .body("timeline[5].eventType", equalTo("EVT-007"))
            .body("timeline[3].detail", containsString("\"dispatchType\":\"TYPE_A\""))
            .body("timeline[4].detail", containsString("\"sentAt\""))
            .body("timeline[4].detail", not(containsString("technicalStatus")))
            .body("dispatch.dispatchType", equalTo("TYPE_A"))
            .body("dispatch.technicalStatus", equalTo("ACK_RECEIVED"))
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

    @Test
    fun `should use type b adapter and stay in polling`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-008",
                  "operatorCode": "UNIMED_ANAPOLIS",
                  "procedureCodes": ["77777777"],
                  "clinicalJustification": "Fluxo tipo B"
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
            .`when`().get("/v1/authorizations/$authorizationId/status")
            .then()
            .statusCode(200)
            .body("dispatch.dispatchType", equalTo("TYPE_B"))
            .body("dispatch.technicalStatus", equalTo("POLLING"))
    }

    @Test
    fun `should poll type b authorization to approved`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-009",
                  "operatorCode": "UNIMED_ANAPOLIS",
                  "procedureCodes": ["88888888"],
                  "clinicalJustification": "Aguardando resposta da operadora"
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
            .body("status", equalTo("AUTHORIZED"))

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/authorizations/$authorizationId/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("AUTHORIZED"))
            .body("timeline.size()", equalTo(7))
            .body("timeline[5].eventType", equalTo("EVT-008"))
            .body("timeline[6].eventType", equalTo("EVT-009"))
            .body("timeline[5].detail", containsString("\"externalStatus\":\"APPROVED\""))
    }

    @Test
    fun `should poll type b authorization to denied`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-010",
                  "operatorCode": "ALLIANZ_SAUDE",
                  "procedureCodes": ["99999999"],
                  "clinicalJustification": "Fluxo de negativa"
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
            .body("status", equalTo("DENIED"))

        given()
            .header("X-Tenant-Id", tenantId)
            .`when`().get("/v1/authorizations/$authorizationId/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("DENIED"))
            .body("timeline.size()", equalTo(7))
            .body("timeline[5].eventType", equalTo("EVT-008"))
            .body("timeline[6].eventType", equalTo("EVT-010"))
            .body("timeline[5].detail", containsString("\"externalStatus\":\"DENIED\""))
    }

    @Test
    fun `should return conflict when cancelling final authorization`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-011",
                  "operatorCode": "ALLIANZ_SAUDE",
                  "procedureCodes": ["11112222"],
                  "clinicalJustification": "Nao deve permitir cancelamento apos negativa"
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
            .body("status", equalTo("DENIED"))

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .contentType("application/json")
            .body("""{"reason":"Tentativa tardia"}""")
            .`when`().post("/v1/authorizations/$authorizationId/cancel")
            .then()
            .statusCode(409)
    }

    @Test
    fun `should return conflict when cancelling already cancelled authorization`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-012",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["11113333"],
                  "clinicalJustification": "Cancelamento unico permitido"
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
            .body("""{"reason":"Primeiro cancelamento"}""")
            .`when`().post("/v1/authorizations/$authorizationId/cancel")
            .then()
            .statusCode(200)
            .body("status", equalTo("CANCELLED"))

        given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .contentType("application/json")
            .body("""{"reason":"Tentativa duplicada"}""")
            .`when`().post("/v1/authorizations/$authorizationId/cancel")
            .then()
            .statusCode(409)
    }
}
