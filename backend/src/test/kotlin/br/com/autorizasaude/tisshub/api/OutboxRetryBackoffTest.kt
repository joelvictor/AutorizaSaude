package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@TestProfile(OutboxBackoffTestProfile::class)
class OutboxRetryBackoffTest {
    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `should block immediate retry until next attempt window`() {
        val tenantId = UUID.randomUUID()

        given()
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Idempotency-Key", "idem-${UUID.randomUUID()}")
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-OUTBOX-BACKOFF",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["55554444"],
                  "clinicalJustification": "Teste backoff outbox"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)

        given()
            .`when`().post("/v1/operations/outbox/process")
            .then()
            .statusCode(200)

        val firstState = findLatestEvt002State(tenantId)
        assertEquals(1, firstState.publishAttempts)
        assertNotNull(firstState.nextAttemptAt)
        assertNull(firstState.deadLetterAt)
        assertTrue(firstState.nextAttemptAt!!.isAfter(OffsetDateTime.now().minusSeconds(1)))

        given()
            .`when`().post("/v1/operations/outbox/process")
            .then()
            .statusCode(200)

        val secondState = findLatestEvt002State(tenantId)
        assertEquals(1, secondState.publishAttempts)
        assertEquals(firstState.nextAttemptAt, secondState.nextAttemptAt)
        assertNull(secondState.deadLetterAt)
    }

    private fun findLatestEvt002State(tenantId: UUID): OutboxAttemptState {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select publish_attempts, next_attempt_at, dead_letter_at
                from outbox_events
                where tenant_id = ? and event_type = 'EVT-002'
                order by occurred_at desc
                limit 1
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return OutboxAttemptState(
                        publishAttempts = resultSet.getInt("publish_attempts"),
                        nextAttemptAt = resultSet.getObject("next_attempt_at", OffsetDateTime::class.java),
                        deadLetterAt = resultSet.getObject("dead_letter_at", OffsetDateTime::class.java)
                    )
                }
            }
        }
    }
}

data class OutboxAttemptState(
    val publishAttempts: Int,
    val nextAttemptAt: OffsetDateTime?,
    val deadLetterAt: OffsetDateTime?
)
