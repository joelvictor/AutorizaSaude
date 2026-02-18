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
    val publishAttempts: Int,
    val nextAttemptAt: OffsetDateTime?
)

data class OutboxProcessingStats(
    val pending: Long,
    val published: Long,
    val deadLetter: Long
)

data class OutboxDeadLetterEntry(
    val outboxEventId: Long,
    val eventId: UUID,
    val eventType: String,
    val failureReason: String,
    val failedAt: OffsetDateTime
)

@ApplicationScoped
class OutboxEventRepository(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) {
    fun append(aggregateType: String, aggregateId: UUID, event: DomainEvent) {
        dataSource.connection.use { connection ->
            appendWithConnection(connection, aggregateType, aggregateId, event)
        }
    }

    fun findDeadLetters(tenantId: UUID, limit: Int): List<OutboxDeadLetterEntry> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select outbox_event_id, event_id, event_type, failure_reason, failed_at
                from outbox_dead_letters
                where tenant_id = ?
                order by failed_at desc
                limit ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setInt(2, limit)
                statement.executeQuery().use { resultSet ->
                    val entries = mutableListOf<OutboxDeadLetterEntry>()
                    while (resultSet.next()) {
                        entries += OutboxDeadLetterEntry(
                            outboxEventId = resultSet.getLong("outbox_event_id"),
                            eventId = resultSet.getObject("event_id", UUID::class.java),
                            eventType = resultSet.getString("event_type"),
                            failureReason = resultSet.getString("failure_reason"),
                            failedAt = resultSet.getObject("failed_at", OffsetDateTime::class.java)
                        )
                    }
                    return entries
                }
            }
        }
    }

    fun requeueDeadLetters(tenantId: UUID, correlationId: UUID, limit: Int): Int {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val ids = mutableListOf<Long>()
                connection.prepareStatement(
                    """
                    select outbox_event_id
                    from outbox_dead_letters
                    where tenant_id = ?
                    order by failed_at asc
                    limit ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, tenantId)
                    statement.setInt(2, limit)
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            ids += resultSet.getLong("outbox_event_id")
                        }
                    }
                }

                var requeued = 0
                ids.forEach { outboxEventId ->
                    val updated = connection.prepareStatement(
                        """
                        update outbox_events
                        set dead_letter_at = null, publish_attempts = 0, last_error = null, published_at = null, next_attempt_at = null
                        where id = ?
                          and tenant_id = ?
                          and dead_letter_at is not null
                          and published_at is null
                        """.trimIndent()
                    ).use { statement ->
                        statement.setLong(1, outboxEventId)
                        statement.setObject(2, tenantId)
                        statement.executeUpdate()
                    }

                    if (updated > 0) {
                        connection.prepareStatement(
                            """
                            delete from outbox_dead_letters
                            where outbox_event_id = ? and tenant_id = ?
                            """.trimIndent()
                        ).use { statement ->
                            statement.setLong(1, outboxEventId)
                            statement.setObject(2, tenantId)
                            statement.executeUpdate()
                        }
                        requeued += 1
                    }
                }

                if (requeued > 0) {
                    appendWithConnection(
                        connection = connection,
                        aggregateType = "OUTBOX",
                        aggregateId = tenantId,
                        event = DomainEvent(
                            eventId = UUID.randomUUID(),
                            eventType = "EVT-016",
                            eventVersion = 1,
                            occurredAt = OffsetDateTime.now(),
                            tenantId = tenantId,
                            correlationId = correlationId,
                            payload = mapOf(
                                "aggregateType" to "OUTBOX",
                                "aggregateId" to tenantId,
                                "action" to "DEAD_LETTER_REQUEUED",
                                "actor" to "system:outbox-ops",
                                "requeued" to requeued
                            )
                        )
                    )
                }

                connection.commit()
                return requeued
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun appendWithConnection(
        connection: java.sql.Connection,
        aggregateType: String,
        aggregateId: UUID,
        event: DomainEvent
    ) {
        insertOutboxEvent(connection, aggregateType, aggregateId, event)

        insertAuditTrail(
            connection = connection,
            tenantId = event.tenantId,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            action = event.eventType,
            actor = event.payload["actor"] as? String ?: "system:audit-module",
            correlationId = event.correlationId,
            metadata = mapOf(
                "eventId" to event.eventId,
                "eventVersion" to event.eventVersion,
                "occurredAt" to event.occurredAt.toString(),
                "payload" to event.payload
            )
        )

        if (event.eventType != "EVT-016") {
            val actor = "system:audit-module"

            val auditEvent = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-016",
                eventVersion = 1,
                occurredAt = OffsetDateTime.now(),
                tenantId = event.tenantId,
                correlationId = event.correlationId,
                payload = mapOf(
                    "aggregateType" to aggregateType,
                    "aggregateId" to aggregateId,
                    "action" to event.eventType,
                    "actor" to actor
                )
            )
            insertOutboxEvent(connection, "AUDIT", aggregateId, auditEvent)
        }
    }

    private fun insertOutboxEvent(
        connection: java.sql.Connection,
        aggregateType: String,
        aggregateId: UUID,
        event: DomainEvent
    ) {
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

    private fun insertAuditTrail(
        connection: java.sql.Connection,
        tenantId: UUID,
        aggregateType: String,
        aggregateId: UUID,
        action: String,
        actor: String,
        correlationId: UUID,
        metadata: Map<String, Any?>
    ) {
        connection.prepareStatement(
            """
            insert into audit_trail (
              tenant_id, aggregate_type, aggregate_id, action, actor, correlation_id, metadata
            ) values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, tenantId)
            statement.setString(2, aggregateType)
            statement.setObject(3, aggregateId)
            statement.setString(4, action)
            statement.setString(5, actor)
            statement.setObject(6, correlationId)
            statement.setString(7, objectMapper.writeValueAsString(metadata))
            statement.executeUpdate()
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
                select id, event_id, tenant_id, event_type, payload, publish_attempts, next_attempt_at
                from outbox_events
                where published_at is null and dead_letter_at is null
                  and (next_attempt_at is null or next_attempt_at <= ?)
                order by occurred_at asc
                limit ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, OffsetDateTime.now())
                statement.setInt(2, limit)
                statement.executeQuery().use { resultSet ->
                    val events = mutableListOf<OutboxPendingEvent>()
                    while (resultSet.next()) {
                        events += OutboxPendingEvent(
                            id = resultSet.getLong("id"),
                            eventId = resultSet.getObject("event_id", UUID::class.java),
                            tenantId = resultSet.getObject("tenant_id", UUID::class.java),
                            eventType = resultSet.getString("event_type"),
                            payload = resultSet.getString("payload"),
                            publishAttempts = resultSet.getInt("publish_attempts"),
                            nextAttemptAt = resultSet.getObject("next_attempt_at", OffsetDateTime::class.java)
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
                set published_at = ?, last_error = null, next_attempt_at = null
                where id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, publishedAt)
                statement.setLong(2, id)
                statement.executeUpdate()
            }
        }
    }

    fun markFailure(
        event: OutboxPendingEvent,
        reason: String,
        maxAttempts: Int,
        nextAttemptAt: OffsetDateTime?
    ): Boolean {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val nextAttempts = event.publishAttempts + 1
                val movedToDeadLetter = nextAttempts >= maxAttempts
                val failedAt = OffsetDateTime.now()
                connection.prepareStatement(
                    """
                    update outbox_events
                    set publish_attempts = ?, last_error = ?, dead_letter_at = ?, next_attempt_at = ?
                    where id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setInt(1, nextAttempts)
                    statement.setString(2, reason)
                    statement.setObject(3, if (movedToDeadLetter) failedAt else null)
                    statement.setObject(4, if (movedToDeadLetter) null else nextAttemptAt)
                    statement.setLong(5, event.id)
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
