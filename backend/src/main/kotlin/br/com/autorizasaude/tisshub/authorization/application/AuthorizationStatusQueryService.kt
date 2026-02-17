package br.com.autorizasaude.tisshub.authorization.application

import br.com.autorizasaude.tisshub.authorization.domain.AuthorizationStatus
import br.com.autorizasaude.tisshub.authorization.infrastructure.AuthorizationRepository
import br.com.autorizasaude.tisshub.authorization.infrastructure.OutboxEventRepository
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorDispatchService
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.UUID

data class AuthorizationTimelineItem(
    val at: OffsetDateTime,
    val eventType: String,
    val detail: String
)

data class DispatchStatusSnapshot(
    val dispatchId: UUID,
    val dispatchType: String,
    val technicalStatus: String,
    val attemptCount: Int,
    val externalProtocol: String?
)

data class AuthorizationStatusView(
    val authorizationId: UUID,
    val status: AuthorizationStatus,
    val timeline: List<AuthorizationTimelineItem>,
    val dispatch: DispatchStatusSnapshot?
)

@ApplicationScoped
class AuthorizationStatusQueryService(
    private val authorizationRepository: AuthorizationRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val operatorDispatchService: OperatorDispatchService
) {
    fun getStatus(tenantId: UUID, authorizationId: UUID): AuthorizationStatusView? {
        val authorization = authorizationRepository.findById(tenantId, authorizationId) ?: return null
        val timeline = outboxEventRepository.findTimeline(
            tenantId = tenantId,
            aggregateType = "AUTHORIZATION",
            aggregateId = authorizationId
        ).map {
            AuthorizationTimelineItem(
                at = it.occurredAt,
                eventType = it.eventType,
                detail = it.payload
            )
        }

        val dispatch = operatorDispatchService.findLatestDispatch(tenantId, authorizationId)?.let {
            DispatchStatusSnapshot(
                dispatchId = it.dispatchId,
                dispatchType = it.dispatchType.name,
                technicalStatus = it.technicalStatus.name,
                attemptCount = it.attemptCount,
                externalProtocol = it.externalProtocol
            )
        }

        return AuthorizationStatusView(
            authorizationId = authorization.authorizationId,
            status = authorization.status,
            timeline = timeline,
            dispatch = dispatch
        )
    }
}
