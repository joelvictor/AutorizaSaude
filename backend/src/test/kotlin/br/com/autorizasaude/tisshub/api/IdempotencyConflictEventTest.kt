package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
class IdempotencyConflictEventTest {
    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `should include authorizationId in evt 015 payload`() {
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
                  "patientId": "P-IDEMP-1",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["12121212"],
                  "clinicalJustification": "Primeira chamada"
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
                  "patientId": "P-IDEMP-1",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["34343434"],
                  "clinicalJustification": "Payload divergente"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(409)

        val payload = findLatestEvt015Payload(UUID.fromString(tenantId))
        assertTrue(payload.contains("\"authorizationId\""))
        assertTrue(!payload.contains("\"authorizationId\":null"))
    }

    private fun findLatestEvt015Payload(tenantId: UUID): String {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select payload
                from outbox_events
                where tenant_id = ? and event_type = 'EVT-015'
                order by occurred_at desc
                limit 1
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getString("payload")
                }
            }
        }
    }
}
