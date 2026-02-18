package br.com.autorizasaude.tisshub.operatordispatch.domain

import java.time.OffsetDateTime
import java.util.UUID

data class OperatorDispatch(
    val dispatchId: UUID,
    val tenantId: UUID,
    val authorizationId: UUID,
    val operatorCode: String,
    val dispatchType: DispatchType,
    val technicalStatus: TechnicalStatus,
    val attemptCount: Int,
    val externalProtocol: String?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val nextAttemptAt: OffsetDateTime?,
    val sentAt: OffsetDateTime?,
    val ackAt: OffsetDateTime?,
    val completedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
