package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
class IdempotencyPendingConflictTest {
    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `should return in-progress conflict and avoid evt 015 when idempotency is pending`() {
        val tenantId = UUID.randomUUID()
        val idempotencyKey = "idem-${UUID.randomUUID()}"

        insertPendingIdempotency(tenantId, idempotencyKey, requestHash = "any-hash")

        given()
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", idempotencyKey)
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-IDEMP-PENDING",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["56565656"],
                  "clinicalJustification": "Conflito enquanto chave esta em processamento"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(409)

        assertEquals(0, countEvt015ByTenantAndKey(tenantId, idempotencyKey))
    }

    private fun insertPendingIdempotency(tenantId: UUID, key: String, requestHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into idempotency_keys (tenant_id, idempotency_key, request_hash)
                values (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setString(2, key)
                statement.setString(3, requestHash)
                statement.executeUpdate()
            }
        }
    }

    private fun countEvt015ByTenantAndKey(tenantId: UUID, key: String): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select count(*) as total
                from outbox_events
                where tenant_id = ?
                  and event_type = 'EVT-015'
                  and payload like ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setString(2, "%\"idempotencyKey\":\"$key\"%")
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getInt("total")
                }
            }
        }
    }
}
