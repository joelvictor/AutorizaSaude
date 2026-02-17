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

data class OutboxPendingEvent(
    val id: Long,
    val eventId: UUID,
    val tenantId: UUID,
    val eventType: String,
    val payload: String,
    val publishAttempts: Int
)

data class OutboxProcessingStats(
    val pending: Long,
    val published: Long,
    val deadLetter: Long
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

    fun findPending(limit: Int): List<OutboxPendingEvent> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select id, event_id, tenant_id, event_type, payload, publish_attempts
                from outbox_events
                where published_at is null and dead_letter_at is null
                order by occurred_at asc
                limit ?
                """.trimIndent()
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { resultSet ->
                    val events = mutableListOf<OutboxPendingEvent>()
                    while (resultSet.next()) {
                        events += OutboxPendingEvent(
                            id = resultSet.getLong("id"),
                            eventId = resultSet.getObject("event_id", UUID::class.java),
                            tenantId = resultSet.getObject("tenant_id", UUID::class.java),
                            eventType = resultSet.getString("event_type"),
                            payload = resultSet.getString("payload"),
                            publishAttempts = resultSet.getInt("publish_attempts")
                        )
                    }
                    return events
                }
            }
        }
    }

    fun markPublished(id: Long, publishedAt: OffsetDateTime) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                update outbox_events
                set published_at = ?, last_error = null
                where id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, publishedAt)
                statement.setLong(2, id)
                statement.executeUpdate()
            }
        }
    }

    fun markFailure(event: OutboxPendingEvent, reason: String, maxAttempts: Int): Boolean {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val nextAttempts = event.publishAttempts + 1
                val movedToDeadLetter = nextAttempts >= maxAttempts
                connection.prepareStatement(
                    """
                    update outbox_events
                    set publish_attempts = ?, last_error = ?, dead_letter_at = ?
                    where id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setInt(1, nextAttempts)
                    statement.setString(2, reason)
                    statement.setObject(3, if (movedToDeadLetter) OffsetDateTime.now() else null)
                    statement.setLong(4, event.id)
                    statement.executeUpdate()
                }

                if (movedToDeadLetter) {
                    connection.prepareStatement(
                        """
                        insert into outbox_dead_letters (
                          outbox_event_id, event_id, tenant_id, event_type, payload, failure_reason
                        ) values (?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setLong(1, event.id)
                        statement.setObject(2, event.eventId)
                        statement.setObject(3, event.tenantId)
                        statement.setString(4, event.eventType)
                        statement.setString(5, event.payload)
                        statement.setString(6, reason)
                        statement.executeUpdate()
                    }
                }

                connection.commit()
                return movedToDeadLetter
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun stats(): OutboxProcessingStats {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select
                  coalesce(sum(case when published_at is null and dead_letter_at is null then 1 else 0 end), 0) as pending,
                  coalesce(sum(case when published_at is not null then 1 else 0 end), 0) as published,
                  coalesce(sum(case when dead_letter_at is not null then 1 else 0 end), 0) as dead_letter
                from outbox_events
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    resultSet.next()
                    return OutboxProcessingStats(
                        pending = resultSet.getLong("pending"),
                        published = resultSet.getLong("published"),
                        deadLetter = resultSet.getLong("dead_letter")
                    )
                }
            }
        }
    }
}
