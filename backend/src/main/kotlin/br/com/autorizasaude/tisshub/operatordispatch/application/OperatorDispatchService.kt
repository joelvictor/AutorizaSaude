package br.com.autorizasaude.tisshub.operatordispatch.application

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapter
import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import br.com.autorizasaude.tisshub.operatordispatch.infrastructure.OperatorDispatchRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
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

    private fun resolveDispatchType(operatorCode: String): DispatchType {
        return when (operatorCode.trim().uppercase()) {
            "BRADESCO", "SULAMERICA", "AMIL", "PORTO", "OMINT" -> DispatchType.TYPE_A
            "UNIMED", "ALLIANZ", "CAREPLUS", "MEDISERVICE" -> DispatchType.TYPE_B
            else -> DispatchType.TYPE_C
        }
    }
}
