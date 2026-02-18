package br.com.autorizasaude.tisshub.observability.infrastructure

import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

data class DispatchLatencySample(
    val operatorCode: String,
    val dispatchType: String,
    val latencyMs: Long
)

data class DispatchSuccessBucket(
    val operatorCode: String,
    val dispatchType: String,
    val successful: Long,
    val total: Long
)

@ApplicationScoped
class ObservabilityRepository(
    private val dataSource: DataSource
) {
    fun fetchDispatchLatencySamples(tenantId: UUID): List<DispatchLatencySample> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select operator_code, dispatch_type, sent_at, completed_at
                from operator_dispatches
                where tenant_id = ?
                  and sent_at is not null
                  and completed_at is not null
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.executeQuery().use { resultSet ->
                    val rows = mutableListOf<DispatchLatencySample>()
                    while (resultSet.next()) {
                        val sentAt = resultSet.getObject("sent_at", OffsetDateTime::class.java)
                        val completedAt = resultSet.getObject("completed_at", OffsetDateTime::class.java)
                        val latencyMs = Duration.between(sentAt, completedAt).toMillis().coerceAtLeast(0L)
                        rows += DispatchLatencySample(
                            operatorCode = resultSet.getString("operator_code"),
                            dispatchType = resultSet.getString("dispatch_type"),
                            latencyMs = latencyMs
                        )
                    }
                    return rows
                }
            }
        }
    }

    fun fetchDispatchSuccessBuckets(tenantId: UUID): List<DispatchSuccessBucket> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select operator_code, dispatch_type,
                       coalesce(sum(case when technical_status in ('ACK_RECEIVED', 'POLLING', 'COMPLETED') then 1 else 0 end), 0) as successful,
                       count(*) as total
                from operator_dispatches
                where tenant_id = ?
                group by operator_code, dispatch_type
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.executeQuery().use { resultSet ->
                    val rows = mutableListOf<DispatchSuccessBucket>()
                    while (resultSet.next()) {
                        rows += DispatchSuccessBucket(
                            operatorCode = resultSet.getString("operator_code"),
                            dispatchType = resultSet.getString("dispatch_type"),
                            successful = resultSet.getLong("successful"),
                            total = resultSet.getLong("total")
                        )
                    }
                    return rows
                }
            }
        }
    }

    fun countDispatchRetryQueue(tenantId: UUID): Long =
        scalarCount(
            tenantId = tenantId,
            sql = """
                select count(*) as c
                from operator_dispatches
                where tenant_id = ?
                  and technical_status = 'TECHNICAL_ERROR'
                  and next_attempt_at is not null
            """.trimIndent()
        )

    fun countDispatchDeadLetter(tenantId: UUID): Long =
        scalarCount(
            tenantId = tenantId,
            sql = """
                select count(*) as c
                from operator_dispatches
                where tenant_id = ?
                  and technical_status = 'DLQ'
            """.trimIndent()
        )

    fun countOutboxRetryQueue(tenantId: UUID): Long =
        scalarCount(
            tenantId = tenantId,
            sql = """
                select count(*) as c
                from outbox_events
                where tenant_id = ?
                  and published_at is null
                  and dead_letter_at is null
                  and next_attempt_at is not null
            """.trimIndent()
        )

    fun countOutboxDeadLetter(tenantId: UUID): Long =
        scalarCount(
            tenantId = tenantId,
            sql = """
                select count(*) as c
                from outbox_dead_letters
                where tenant_id = ?
            """.trimIndent()
        )

    private fun scalarCount(tenantId: UUID, sql: String): Long {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, tenantId)
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getLong("c")
                }
            }
        }
    }
}
