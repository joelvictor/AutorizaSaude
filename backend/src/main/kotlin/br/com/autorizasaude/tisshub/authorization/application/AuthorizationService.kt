package br.com.autorizasaude.tisshub.authorization.application

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.authorization.domain.AuthorizationStatus
import br.com.autorizasaude.tisshub.authorization.infrastructure.AuthorizationRepository
import br.com.autorizasaude.tisshub.authorization.infrastructure.IdempotencyRepository
import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxEventRepository
import br.com.autorizasaude.tisshub.operatordispatch.application.ExternalAuthorizationStatus
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorDispatchService
import br.com.autorizasaude.tisshub.operatordispatch.application.PollDispatchFailedException
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import br.com.autorizasaude.tisshub.shared.events.DomainEvent
import br.com.autorizasaude.tisshub.tissguide.application.TissGuideService
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

data class PollAuthorizationStatusCommand(
    val tenantId: UUID,
    val correlationId: UUID,
    val authorizationId: UUID
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
    private val operatorDispatchService: OperatorDispatchService,
    private val tissGuideService: TissGuideService,
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
        val authorizationDraft = Authorization(
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

        authorizationRepository.insert(authorizationDraft)
        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = authorizationDraft.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-001",
                eventVersion = 1,
                occurredAt = now,
                tenantId = command.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to authorizationDraft.authorizationId,
                    "patientId" to authorizationDraft.patientId,
                    "procedureCodes" to authorizationDraft.procedureCodes
                )
            )
        )

        val validated = authorizationDraft.copy(
            status = AuthorizationStatus.VALIDATED,
            updatedAt = OffsetDateTime.now()
        )
        authorizationRepository.updateStatus(validated)
        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = validated.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-002",
                eventVersion = 1,
                occurredAt = validated.updatedAt,
                tenantId = validated.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to validated.authorizationId,
                    "validationSummary" to "business-rules-ok"
                )
            )
        )

        val tissResult = tissGuideService.generateAndValidate(validated)
        if (!tissResult.valid) {
            val failed = validated.copy(
                status = AuthorizationStatus.FAILED_TECHNICAL,
                updatedAt = OffsetDateTime.now()
            )
            authorizationRepository.updateStatus(failed)
            outboxEventRepository.append(
                aggregateType = "AUTHORIZATION",
                aggregateId = failed.authorizationId,
                event = DomainEvent(
                    eventId = UUID.randomUUID(),
                    eventType = "EVT-004",
                    eventVersion = 1,
                    occurredAt = failed.updatedAt,
                    tenantId = failed.tenantId,
                    correlationId = command.correlationId,
                    payload = mapOf(
                        "authorizationId" to failed.authorizationId,
                        "tissGuideId" to tissResult.tissGuide.tissGuideId,
                        "errors" to listOf(tissResult.tissGuide.validationReport ?: "unknown-validation-error")
                    )
                )
            )
            idempotencyRepository.linkAuthorization(
                tenantId = command.tenantId,
                idempotencyKey = command.idempotencyKey,
                authorizationId = failed.authorizationId,
                responseSnapshot = objectMapper.writeValueAsString(
                    mapOf(
                        "authorizationId" to failed.authorizationId,
                        "status" to failed.status.name
                    )
                )
            )
            return CreateAuthorizationResult(failed, replayed = false)
        }

        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = validated.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-003",
                eventVersion = 1,
                occurredAt = OffsetDateTime.now(),
                tenantId = validated.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to validated.authorizationId,
                    "tissGuideId" to tissResult.tissGuide.tissGuideId,
                    "xmlHash" to tissResult.tissGuide.xmlHash,
                    "tissVersion" to tissResult.tissGuide.tissVersion
                )
            )
        )

        val dispatch = operatorDispatchService.requestDispatch(validated)
        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = validated.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-005",
                eventVersion = 1,
                occurredAt = dispatch.createdAt,
                tenantId = validated.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to validated.authorizationId,
                    "operatorCode" to validated.operatorCode,
                    "dispatchType" to dispatch.dispatchType.name
                )
            )
        )

        if (dispatch.technicalStatus == TechnicalStatus.TECHNICAL_ERROR) {
            val failed = validated.copy(
                status = AuthorizationStatus.FAILED_TECHNICAL,
                updatedAt = OffsetDateTime.now()
            )
            authorizationRepository.updateStatus(failed)
            outboxEventRepository.append(
                aggregateType = "AUTHORIZATION",
                aggregateId = failed.authorizationId,
                event = DomainEvent(
                    eventId = UUID.randomUUID(),
                    eventType = "EVT-013",
                    eventVersion = 1,
                    occurredAt = failed.updatedAt,
                    tenantId = failed.tenantId,
                    correlationId = command.correlationId,
                    payload = mapOf(
                        "authorizationId" to failed.authorizationId,
                        "dispatchId" to dispatch.dispatchId,
                        "errorCode" to "DISPATCH_ERROR",
                        "errorMessage" to "Dispatch adapter failed"
                    )
                )
            )

            idempotencyRepository.linkAuthorization(
                tenantId = command.tenantId,
                idempotencyKey = command.idempotencyKey,
                authorizationId = failed.authorizationId,
                responseSnapshot = objectMapper.writeValueAsString(
                    mapOf(
                        "authorizationId" to failed.authorizationId,
                        "status" to failed.status.name
                    )
                )
            )
            return CreateAuthorizationResult(failed, replayed = false)
        }

        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = validated.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-006",
                eventVersion = 1,
                occurredAt = dispatch.updatedAt,
                tenantId = validated.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to validated.authorizationId,
                    "dispatchId" to dispatch.dispatchId,
                    "attempt" to dispatch.attemptCount,
                    "sentAt" to dispatch.updatedAt.toString()
                )
            )
        )

        if (dispatch.technicalStatus == TechnicalStatus.ACK_RECEIVED) {
            outboxEventRepository.append(
                aggregateType = "AUTHORIZATION",
                aggregateId = validated.authorizationId,
                event = DomainEvent(
                    eventId = UUID.randomUUID(),
                    eventType = "EVT-007",
                    eventVersion = 1,
                    occurredAt = OffsetDateTime.now(),
                    tenantId = validated.tenantId,
                    correlationId = command.correlationId,
                    payload = mapOf(
                        "authorizationId" to validated.authorizationId,
                        "dispatchId" to dispatch.dispatchId,
                        "externalProtocol" to dispatch.externalProtocol
                    )
                )
            )
        }

        val dispatched = validated.copy(
            status = if (dispatch.technicalStatus == TechnicalStatus.POLLING) {
                AuthorizationStatus.PENDING_OPERATOR
            } else {
                AuthorizationStatus.DISPATCHED
            },
            updatedAt = OffsetDateTime.now()
        )
        authorizationRepository.updateStatus(dispatched)

        idempotencyRepository.linkAuthorization(
            tenantId = command.tenantId,
            idempotencyKey = command.idempotencyKey,
            authorizationId = dispatched.authorizationId,
            responseSnapshot = objectMapper.writeValueAsString(
                mapOf(
                    "authorizationId" to dispatched.authorizationId,
                    "status" to dispatched.status.name
                )
            )
        )
        return CreateAuthorizationResult(dispatched, replayed = false)
    }

    fun getById(tenantId: UUID, id: UUID): Authorization? = authorizationRepository.findById(tenantId, id)

    @Transactional
    fun pollStatus(command: PollAuthorizationStatusCommand): Authorization? {
        val current = authorizationRepository.findById(command.tenantId, command.authorizationId) ?: return null
        if (current.status == AuthorizationStatus.AUTHORIZED ||
            current.status == AuthorizationStatus.DENIED ||
            current.status == AuthorizationStatus.CANCELLED ||
            current.status == AuthorizationStatus.EXPIRED ||
            current.status == AuthorizationStatus.FAILED_TECHNICAL
        ) {
            return current
        }

        val dispatch = operatorDispatchService.findLatestDispatch(command.tenantId, command.authorizationId)
            ?: return current

        val pollResult = try {
            operatorDispatchService.pollDispatch(dispatch)
        } catch (ex: PollDispatchFailedException) {
            val failedDispatch = ex.failedDispatch
            outboxEventRepository.append(
                aggregateType = "AUTHORIZATION",
                aggregateId = current.authorizationId,
                event = DomainEvent(
                    eventId = UUID.randomUUID(),
                    eventType = "EVT-013",
                    eventVersion = 1,
                    occurredAt = failedDispatch.updatedAt,
                    tenantId = current.tenantId,
                    correlationId = command.correlationId,
                    payload = mapOf(
                        "authorizationId" to current.authorizationId,
                        "dispatchId" to failedDispatch.dispatchId,
                        "errorCode" to (failedDispatch.lastErrorCode ?: "POLL_ERROR"),
                        "errorMessage" to (failedDispatch.lastErrorMessage ?: "Operator polling failed")
                    )
                )
            )

            if (ex.movedToDeadLetter) {
                val failed = current.copy(
                    status = AuthorizationStatus.FAILED_TECHNICAL,
                    updatedAt = OffsetDateTime.now()
                )
                authorizationRepository.updateStatus(failed)
                outboxEventRepository.append(
                    aggregateType = "AUTHORIZATION",
                    aggregateId = failed.authorizationId,
                    event = DomainEvent(
                        eventId = UUID.randomUUID(),
                        eventType = "EVT-014",
                        eventVersion = 1,
                        occurredAt = failed.updatedAt,
                        tenantId = failed.tenantId,
                        correlationId = command.correlationId,
                        payload = mapOf(
                            "authorizationId" to failed.authorizationId,
                            "dispatchId" to failedDispatch.dispatchId,
                            "attempts" to failedDispatch.attemptCount,
                            "lastErrorCode" to (failedDispatch.lastErrorCode ?: "POLL_ERROR")
                        )
                    )
                )
                return failed
            }

            outboxEventRepository.append(
                aggregateType = "AUTHORIZATION",
                aggregateId = current.authorizationId,
                event = DomainEvent(
                    eventId = UUID.randomUUID(),
                    eventType = "EVT-012",
                    eventVersion = 1,
                    occurredAt = OffsetDateTime.now(),
                    tenantId = current.tenantId,
                    correlationId = command.correlationId,
                    payload = mapOf(
                        "authorizationId" to current.authorizationId,
                        "dispatchId" to failedDispatch.dispatchId,
                        "nextAttemptAt" to (failedDispatch.nextAttemptAt?.toString() ?: OffsetDateTime.now().toString())
                    )
                )
            )

            return if (current.status == AuthorizationStatus.PENDING_OPERATOR) {
                current
            } else {
                val pending = current.copy(
                    status = AuthorizationStatus.PENDING_OPERATOR,
                    updatedAt = OffsetDateTime.now()
                )
                authorizationRepository.updateStatus(pending)
                pending
            }
        } catch (ex: Exception) {
            val failed = current.copy(
                status = AuthorizationStatus.FAILED_TECHNICAL,
                updatedAt = OffsetDateTime.now()
            )
            authorizationRepository.updateStatus(failed)
            outboxEventRepository.append(
                aggregateType = "AUTHORIZATION",
                aggregateId = failed.authorizationId,
                event = DomainEvent(
                    eventId = UUID.randomUUID(),
                    eventType = "EVT-013",
                    eventVersion = 1,
                    occurredAt = failed.updatedAt,
                    tenantId = failed.tenantId,
                    correlationId = command.correlationId,
                    payload = mapOf(
                        "authorizationId" to failed.authorizationId,
                        "dispatchId" to dispatch.dispatchId,
                        "errorCode" to "POLL_ERROR",
                        "errorMessage" to (ex.message ?: "Operator polling failed")
                    )
                )
            )
            return failed
        }

        outboxEventRepository.append(
            aggregateType = "AUTHORIZATION",
            aggregateId = current.authorizationId,
            event = DomainEvent(
                eventId = UUID.randomUUID(),
                eventType = "EVT-008",
                eventVersion = 1,
                occurredAt = OffsetDateTime.now(),
                tenantId = current.tenantId,
                correlationId = command.correlationId,
                payload = mapOf(
                    "authorizationId" to current.authorizationId,
                    "dispatchId" to dispatch.dispatchId,
                    "externalStatus" to pollResult.externalStatus.name
                )
            )
        )

        return when (pollResult.externalStatus) {
            ExternalAuthorizationStatus.PENDING -> {
                if (current.status == AuthorizationStatus.PENDING_OPERATOR) {
                    current
                } else {
                    val pending = current.copy(
                        status = AuthorizationStatus.PENDING_OPERATOR,
                        updatedAt = OffsetDateTime.now()
                    )
                    authorizationRepository.updateStatus(pending)
                    pending
                }
            }

            ExternalAuthorizationStatus.APPROVED -> {
                val approved = current.copy(
                    status = AuthorizationStatus.AUTHORIZED,
                    updatedAt = OffsetDateTime.now()
                )
                authorizationRepository.updateStatus(approved)
                outboxEventRepository.append(
                    aggregateType = "AUTHORIZATION",
                    aggregateId = approved.authorizationId,
                    event = DomainEvent(
                        eventId = UUID.randomUUID(),
                        eventType = "EVT-009",
                        eventVersion = 1,
                        occurredAt = approved.updatedAt,
                        tenantId = approved.tenantId,
                        correlationId = command.correlationId,
                        payload = mapOf(
                            "authorizationId" to approved.authorizationId,
                            "authorizedAt" to approved.updatedAt.toString(),
                            "operatorReference" to pollResult.operatorReference
                        )
                    )
                )
                approved
            }

            ExternalAuthorizationStatus.DENIED -> {
                val denied = current.copy(
                    status = AuthorizationStatus.DENIED,
                    updatedAt = OffsetDateTime.now()
                )
                authorizationRepository.updateStatus(denied)
                outboxEventRepository.append(
                    aggregateType = "AUTHORIZATION",
                    aggregateId = denied.authorizationId,
                    event = DomainEvent(
                        eventId = UUID.randomUUID(),
                        eventType = "EVT-010",
                        eventVersion = 1,
                        occurredAt = denied.updatedAt,
                        tenantId = denied.tenantId,
                        correlationId = command.correlationId,
                        payload = mapOf(
                            "authorizationId" to denied.authorizationId,
                            "deniedAt" to denied.updatedAt.toString(),
                            "denialReasonCode" to (pollResult.denialReasonCode ?: "UNSPECIFIED"),
                            "denialReason" to (pollResult.denialReason ?: "Denied by operator")
                        )
                    )
                )
                denied
            }
        }
    }

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
