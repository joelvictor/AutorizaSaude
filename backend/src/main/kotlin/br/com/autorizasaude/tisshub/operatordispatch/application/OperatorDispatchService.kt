package br.com.autorizasaude.tisshub.operatordispatch.application

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapter
import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import br.com.autorizasaude.tisshub.operatordispatch.infrastructure.OperatorDispatchRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import java.text.Normalizer
import java.time.OffsetDateTime
import java.util.UUID

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
            createdAt = now,
            updatedAt = now
        )
        repository.insert(created)

        val adapter = adapters.iterator().asSequence().firstOrNull { it.dispatchType == created.dispatchType }
        if (adapter == null) {
            val failed = created.copy(
                technicalStatus = TechnicalStatus.TECHNICAL_ERROR,
                attemptCount = 1,
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
                updatedAt = OffsetDateTime.now()
            )
            repository.update(updated)
            updated
        } catch (_: Exception) {
            val failed = created.copy(
                technicalStatus = TechnicalStatus.TECHNICAL_ERROR,
                attemptCount = 1,
                updatedAt = OffsetDateTime.now()
            )
            repository.update(failed)
            failed
        }
    }

    fun findLatestDispatch(tenantId: UUID, authorizationId: UUID): OperatorDispatch? =
        repository.findLatestByAuthorization(tenantId, authorizationId)

    fun pollDispatch(dispatch: OperatorDispatch): OperatorAdapterPollResult {
        val adapter = adapters.iterator().asSequence().firstOrNull { it.dispatchType == dispatch.dispatchType }
            ?: throw IllegalStateException("No adapter available for dispatch type ${dispatch.dispatchType.name}")
        val pollResult = adapter.poll(dispatch)
        val updated = dispatch.copy(
            technicalStatus = when (pollResult.externalStatus) {
                ExternalAuthorizationStatus.PENDING -> TechnicalStatus.POLLING
                ExternalAuthorizationStatus.APPROVED -> TechnicalStatus.COMPLETED
                ExternalAuthorizationStatus.DENIED -> TechnicalStatus.COMPLETED
            },
            externalProtocol = pollResult.operatorReference ?: dispatch.externalProtocol,
            updatedAt = OffsetDateTime.now()
        )
        repository.update(updated)
        return pollResult
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
