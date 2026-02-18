package br.com.autorizasaude.tisshub.operatordispatch.application

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import br.com.autorizasaude.tisshub.operatordispatch.infrastructure.OperatorDispatchRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import java.text.Normalizer
import java.time.OffsetDateTime
import java.util.UUID

class PollDispatchFailedException(
    val failedDispatch: OperatorDispatch,
    val movedToDeadLetter: Boolean,
    cause: Throwable
) : RuntimeException("Operator polling failed", cause)

@ApplicationScoped
class OperatorDispatchService(
    private val repository: OperatorDispatchRepository,
    private val adapters: Instance<OperatorAdapter>
) {
    fun requestDispatch(authorization: Authorization): OperatorDispatch {
        val now = OffsetDateTime.now()
        val created = OperatorDispatch(
            dispatchId = UUID.randomUUID(),
            tenantId = authorization.tenantId,
            authorizationId = authorization.authorizationId,
            operatorCode = authorization.operatorCode,
            dispatchType = resolveDispatchType(authorization.operatorCode),
            technicalStatus = TechnicalStatus.READY,
            attemptCount = 0,
            externalProtocol = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            nextAttemptAt = null,
            sentAt = null,
            ackAt = null,
            completedAt = null,
            createdAt = now,
            updatedAt = now
        )
        repository.insert(created)

        val adapter = adapters.iterator().asSequence().firstOrNull { it.dispatchType == created.dispatchType }
        if (adapter == null) {
            val failed = created.copy(
                technicalStatus = TechnicalStatus.TECHNICAL_ERROR,
                attemptCount = 1,
                lastErrorCode = "ADAPTER_NOT_FOUND",
                lastErrorMessage = "No adapter available for dispatch type ${created.dispatchType.name}",
                updatedAt = OffsetDateTime.now()
            )
            repository.update(failed)
            return failed
        }

        return try {
            val sendResult = adapter.send(created)
            val updated = created.copy(
                technicalStatus = sendResult.technicalStatus,
                attemptCount = 1,
                externalProtocol = sendResult.externalProtocol,
                lastErrorCode = null,
                lastErrorMessage = null,
                nextAttemptAt = null,
                sentAt = OffsetDateTime.now(),
                ackAt = if (sendResult.technicalStatus == TechnicalStatus.ACK_RECEIVED) OffsetDateTime.now() else null,
                completedAt = if (sendResult.technicalStatus == TechnicalStatus.COMPLETED) OffsetDateTime.now() else null,
                updatedAt = OffsetDateTime.now()
            )
            repository.update(updated)
            updated
        } catch (ex: Exception) {
            val failed = created.copy(
                technicalStatus = TechnicalStatus.TECHNICAL_ERROR,
                attemptCount = 1,
                lastErrorCode = "DISPATCH_ERROR",
                lastErrorMessage = ex.message ?: "Dispatch adapter failed",
                updatedAt = OffsetDateTime.now()
            )
            repository.update(failed)
            failed
        }
    }

    fun findLatestDispatch(tenantId: UUID, authorizationId: UUID): OperatorDispatch? =
        repository.findLatestByAuthorization(tenantId, authorizationId)

    fun pollDispatch(dispatch: OperatorDispatch): OperatorAdapterPollResult {
        return try {
            val adapter = adapters.iterator().asSequence().firstOrNull { it.dispatchType == dispatch.dispatchType }
                ?: throw IllegalStateException("No adapter available for dispatch type ${dispatch.dispatchType.name}")
            val pollResult = adapter.poll(dispatch)
            val completedAt = when (pollResult.externalStatus) {
                ExternalAuthorizationStatus.PENDING -> null
                ExternalAuthorizationStatus.APPROVED -> OffsetDateTime.now()
                ExternalAuthorizationStatus.DENIED -> OffsetDateTime.now()
            }
            val updated = dispatch.copy(
                technicalStatus = when (pollResult.externalStatus) {
                    ExternalAuthorizationStatus.PENDING -> TechnicalStatus.POLLING
                    ExternalAuthorizationStatus.APPROVED -> TechnicalStatus.COMPLETED
                    ExternalAuthorizationStatus.DENIED -> TechnicalStatus.COMPLETED
                },
                externalProtocol = pollResult.operatorReference ?: dispatch.externalProtocol,
                lastErrorCode = null,
                lastErrorMessage = null,
                nextAttemptAt = null,
                ackAt = dispatch.ackAt,
                completedAt = completedAt,
                updatedAt = OffsetDateTime.now()
            )
            repository.update(updated)
            pollResult
        } catch (ex: Exception) {
            val nextAttemptCount = dispatch.attemptCount + 1
            val movedToDeadLetter = nextAttemptCount >= MAX_ATTEMPTS
            val nextAttemptAt = if (movedToDeadLetter) {
                null
            } else {
                OffsetDateTime.now().plusSeconds(resolveBackoffSeconds(nextAttemptCount))
            }
            val failed = dispatch.copy(
                technicalStatus = if (movedToDeadLetter) TechnicalStatus.DLQ else TechnicalStatus.TECHNICAL_ERROR,
                attemptCount = dispatch.attemptCount + 1,
                lastErrorCode = "POLL_ERROR",
                lastErrorMessage = ex.message ?: "Operator polling failed",
                nextAttemptAt = nextAttemptAt,
                updatedAt = OffsetDateTime.now()
            )
            repository.update(failed)
            throw PollDispatchFailedException(
                failedDispatch = failed,
                movedToDeadLetter = movedToDeadLetter,
                cause = ex
            )
        }
    }

    private fun resolveBackoffSeconds(nextAttemptCount: Int): Long {
        val index = (nextAttemptCount - 2).coerceIn(0, RETRY_BACKOFF_SECONDS.lastIndex)
        return RETRY_BACKOFF_SECONDS[index]
    }

    private fun resolveDispatchType(operatorCode: String): DispatchType {
        val normalized = normalizeOperatorCode(operatorCode)
        return when {
            TYPE_A_CODES.contains(normalized) -> DispatchType.TYPE_A
            TYPE_B_CODES.contains(normalized) -> DispatchType.TYPE_B
            else -> DispatchType.TYPE_C
        }
    }

    private fun normalizeOperatorCode(value: String): String {
        val withoutAccents = Normalizer
            .normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutAccents
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    private companion object {
        private const val MAX_ATTEMPTS = 5
        private val RETRY_BACKOFF_SECONDS = listOf(5L, 15L, 45L, 120L, 300L)

        val TYPE_A_CODES = setOf(
            "BRADESCO",
            "BRADESCO_SAUDE",
            "SULAMERICA",
            "SUL_AMERICA",
            "SULAMERICA_SAUDE",
            "AMIL",
            "AMIL_SAUDE",
            "PORTO",
            "PORTO_SEGURO",
            "OMINT"
        )

        val TYPE_B_CODES = setOf(
            "UNIMED",
            "UNIMED_ANAPOLIS",
            "ALLIANZ_SAUDE",
            "CAREPLUS",
            "CARE_PLUS",
            "MEDISERVICE",
            "MEDISERVICE_SAUDE"
        )
    }
}
