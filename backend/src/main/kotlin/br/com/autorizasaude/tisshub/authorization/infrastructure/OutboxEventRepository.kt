package br.com.autorizasaude.tisshub.authorization.infrastructure

import br.com.autorizasaude.tisshub.shared.events.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

data class OutboxEventTimelineEntry(
    val eventType: String,
    val occurredAt: OffsetDateTime,
    val payload: String
)

@ApplicationScoped
class OutboxEventRepository(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) {
    fun append(aggregateType: String, aggregateId: UUID, event: DomainEvent) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into outbox_events (
                  event_id, tenant_id, aggregate_type, aggregate_id,
                  event_type, event_version, correlation_id, payload, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, event.eventId)
                statement.setObject(2, event.tenantId)
                statement.setString(3, aggregateType)
                statement.setObject(4, aggregateId)
                statement.setString(5, event.eventType)
                statement.setInt(6, event.eventVersion)
                statement.setObject(7, event.correlationId)
                statement.setString(8, objectMapper.writeValueAsString(event.payload))
                statement.setObject(9, event.occurredAt)
                statement.executeUpdate()
            }
        }
    }

    fun findTimeline(tenantId: UUID, aggregateType: String, aggregateId: UUID): List<OutboxEventTimelineEntry> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select event_type, occurred_at, payload
                from outbox_events
                where tenant_id = ? and aggregate_type = ? and aggregate_id = ?
                order by occurred_at asc
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setString(2, aggregateType)
                statement.setObject(3, aggregateId)
                statement.executeQuery().use { resultSet ->
                    val timeline = mutableListOf<OutboxEventTimelineEntry>()
                    while (resultSet.next()) {
                        timeline += OutboxEventTimelineEntry(
                            eventType = resultSet.getString("event_type"),
                            occurredAt = resultSet.getObject("occurred_at", OffsetDateTime::class.java),
                            payload = resultSet.getString("payload")
                        )
                    }
                    return timeline
                }
            }
        }
    }
}
