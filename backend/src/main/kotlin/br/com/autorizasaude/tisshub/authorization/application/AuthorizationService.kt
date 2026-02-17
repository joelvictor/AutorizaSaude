package br.com.autorizasaude.tisshub.authorization.application

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.authorization.domain.AuthorizationStatus
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CreateAuthorizationCommand(
    val tenantId: UUID,
    val patientId: String,
    val operatorCode: String,
    val procedureCodes: List<String>,
    val clinicalJustification: String
)

@ApplicationScoped
class AuthorizationService {
    private val store = ConcurrentHashMap<UUID, Authorization>()

    fun create(command: CreateAuthorizationCommand): Authorization {
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
        store[authorization.authorizationId] = authorization
        return authorization
    }

    fun getById(id: UUID): Authorization? = store[id]

    fun cancel(id: UUID): Authorization? {
        val current = store[id] ?: return null
        if (current.status == AuthorizationStatus.AUTHORIZED || current.status == AuthorizationStatus.DENIED) {
            return current
        }
        val cancelled = current.copy(
            status = AuthorizationStatus.CANCELLED,
            updatedAt = OffsetDateTime.now()
        )
        store[id] = cancelled
        return cancelled
    }
}
