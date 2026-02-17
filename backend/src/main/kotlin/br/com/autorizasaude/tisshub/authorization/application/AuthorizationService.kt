package br.com.autorizasaude.tisshub.authorization.application

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.authorization.domain.AuthorizationStatus
import br.com.autorizasaude.tisshub.authorization.infrastructure.AuthorizationRepository
import br.com.autorizasaude.tisshub.authorization.infrastructure.IdempotencyRepository
import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxEventRepository
import br.com.autorizasaude.tisshub.shared.events.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID

data class CreateAuthorizationCommand(
    val tenantId: UUID,
    val correlationId: UUID,
    val idempotencyKey: String,
    val patientId: String,
    val operatorCode: String,
    val procedureCodes: List<String>,
    val clinicalJustification: String
)

data class CancelAuthorizationCommand(
    val tenantId: UUID,
    val correlationId: UUID,
    val authorizationId: UUID,
    val reason: String
)

data class CreateAuthorizationResult(
    val authorization: Authorization,
    val replayed: Boolean
)

class IdempotencyConflictException : RuntimeException("Idempotency key already used with a different payload")
class IdempotencyInProgressException : RuntimeException("Idempotency key is already being processed")

@ApplicationScoped
class AuthorizationService(
    private val authorizationRepository: AuthorizationRepository,
    private val idempotencyRepository: IdempotencyRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun create(command: CreateAuthorizationCommand): CreateAuthorizationResult {
        val requestHash = hashRequest(command)
        val existing = idempotencyRepository.find(command.tenantId, command.idempotencyKey)

        if (existing != null) {
            if (existing.requestHash != requestHash) {
                outboxEventRepository.append(
                    aggregateType = "IDEMPOTENCY",
                    aggregateId = UUID.randomUUID(),
                    event = DomainEvent(
                        eventId = UUID.randomUUID(),
                        eventType = "EVT-015",
                        eventVersion = 1,
                        occurredAt = OffsetDateTime.now(),
                        tenantId = command.tenantId,
                        correlationId = command.correlationId,
                        payload = mapOf(
                            "idempotencyKey" to command.idempotencyKey,
                            "detectedAt" to OffsetDateTime.now().toString()
                        )
                    )
                )
                throw IdempotencyConflictException()
            }

            if (existing.authorizationId != null) {
                val replayed = authorizationRepository.findById(command.tenantId, existing.authorizationId)
                    ?: throw IllegalStateException("Idempotency record references unknown authorization")
                return CreateAuthorizationResult(replayed, replayed = true)
            }
            throw IdempotencyInProgressException()
        } else {
            idempotencyRepository.insertPending(command.tenantId, command.idempotencyKey, requestHash)
        }

        val now = OffsetDateTime.now()
        val authorization = Authorization(
            authorizationId = UUID.randomUUID(),
            tenantId = command.tenantId,
            patientId = command.patientId,
            operatorCode = command.operatorCode,
            procedureCodes = command.procedureCodes,
            clinicalJustification = command.clinicalJustification,
            status = AuthorizationStatus.DRAFT,
            createdAt = now,
            updatedAt = now
        )

        authorizationRepository.insert(authorization)
        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = authorization.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-001",
                eventVersion = 1,
                occurredAt = now,
                tenantId = command.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to authorization.authorizationId,
                    "patientId" to authorization.patientId,
                    "procedureCodes" to authorization.procedureCodes
                )
            )
        )
        idempotencyRepository.linkAuthorization(
            tenantId = command.tenantId,
            idempotencyKey = command.idempotencyKey,
            authorizationId = authorization.authorizationId,
            responseSnapshot = objectMapper.writeValueAsString(
                mapOf(
                    "authorizationId" to authorization.authorizationId,
                    "status" to authorization.status.name
                )
            )
        )
        return CreateAuthorizationResult(authorization, replayed = false)
    }

    fun getById(tenantId: UUID, id: UUID): Authorization? = authorizationRepository.findById(tenantId, id)

    @Transactional
    fun cancel(command: CancelAuthorizationCommand): Authorization? {
        val current = authorizationRepository.findById(command.tenantId, command.authorizationId) ?: return null
        if (current.status == AuthorizationStatus.AUTHORIZED || current.status == AuthorizationStatus.DENIED) {
            return current
        }
        val cancelled = current.copy(
            status = AuthorizationStatus.CANCELLED,
            updatedAt = OffsetDateTime.now()
        )
        authorizationRepository.updateStatus(cancelled)
        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = cancelled.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-011",
                eventVersion = 1,
                occurredAt = cancelled.updatedAt,
                tenantId = command.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to cancelled.authorizationId,
                    "reason" to command.reason,
                    "cancelledAt" to cancelled.updatedAt.toString()
                )
            )
        )
        return cancelled
    }

    private fun hashRequest(command: CreateAuthorizationCommand): String {
        val payload = buildString {
            append(command.tenantId)
            append('|')
            append(command.patientId.trim())
            append('|')
            append(command.operatorCode.trim())
            append('|')
            append(command.procedureCodes.joinToString(",") { it.trim() })
            append('|')
            append(command.clinicalJustification.trim())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
