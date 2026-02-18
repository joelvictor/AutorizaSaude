package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
class AuditTrailIntegrationTest {
    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `should record audit trail and publish evt 016 for authorization events`() {
        val tenantId = UUID.randomUUID().toString()
        val authorizationId = given()
            .header("X-Tenant-Id", tenantId)
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-AUDIT",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["10101010"],
                  "clinicalJustification": "Teste de auditoria"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .extract()
            .path<String>("authorizationId")

        val tenantUuid = UUID.fromString(tenantId)
        val authorizationUuid = UUID.fromString(authorizationId)

        val auditTrailCount = countAuditRows(tenantUuid, authorizationUuid)
        val evt016Count = countEvt016Rows(tenantUuid)
        val correlatedEvt016Count = countCorrelatedEvt016Rows(tenantUuid, authorizationUuid)

        assertEquals(6, auditTrailCount)
        assertTrue(evt016Count >= 6)
        assertTrue(correlatedEvt016Count >= 6)
    }

    private fun countAuditRows(tenantId: UUID, authorizationId: UUID): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select count(*) as total
                from audit_trail
                where tenant_id = ? and aggregate_type = 'AUTHORIZATION' and aggregate_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setObject(2, authorizationId)
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getInt("total")
                }
            }
        }
    }

    private fun countEvt016Rows(tenantId: UUID): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select count(*) as total
                from outbox_events
                where tenant_id = ? and aggregate_type = 'AUDIT' and event_type = 'EVT-016'
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getInt("total")
                }
            }
        }
    }

    private fun countCorrelatedEvt016Rows(tenantId: UUID, authorizationId: UUID): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select count(*) as total
                from outbox_events
                where tenant_id = ?
                  and aggregate_type = 'AUDIT'
                  and event_type = 'EVT-016'
                  and aggregate_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setObject(2, authorizationId)
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getInt("total")
                }
            }
        }
    }
}
