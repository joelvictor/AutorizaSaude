package br.com.autorizasaude.tisshub.api

import br.com.autorizasaude.tisshub.authorization.application.AuthorizationService
import br.com.autorizasaude.tisshub.authorization.application.CancelAuthorizationCommand
import br.com.autorizasaude.tisshub.authorization.application.CreateAuthorizationCommand
import br.com.autorizasaude.tisshub.authorization.application.IdempotencyConflictException
import br.com.autorizasaude.tisshub.authorization.application.IdempotencyInProgressException
import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.authorization.domain.AuthorizationStatus
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

data class CreateAuthorizationRequest(
    val patientId: String = "",
    val operatorCode: String = "",
    val procedureCodes: List<String> = emptyList(),
    val clinicalJustification: String = ""
)

data class CancelAuthorizationRequest(
    val reason: String = ""
)

data class AuthorizationResponse(
    val authorizationId: UUID,
    val tenantId: UUID,
    val status: AuthorizationStatus,
    val operatorCode: String
) {
    companion object {
        fun from(auth: Authorization) = AuthorizationResponse(
            authorizationId = auth.authorizationId,
            tenantId = auth.tenantId,
            status = auth.status,
            operatorCode = auth.operatorCode
        )
    }
}

@Path("/v1/authorizations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class AuthorizationResource(
    private val authorizationService: AuthorizationService
) {

    @POST
    fun create(
        @HeaderParam("X-Tenant-Id") tenantIdHeader: String?,
        @HeaderParam("X-Correlation-Id") correlationIdHeader: String?,
        @HeaderParam("X-Idempotency-Key") idempotencyKeyHeader: String?,
        request: CreateAuthorizationRequest
    ): Response {
        val tenantId = parseRequiredUuidHeader("X-Tenant-Id", tenantIdHeader)
        val correlationId = parseRequiredUuidHeader("X-Correlation-Id", correlationIdHeader)
        val idempotencyKey = idempotencyKeyHeader?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw WebApplicationException("X-Idempotency-Key header is required", Response.Status.BAD_REQUEST)
        validateCreateRequest(request)

        return try {
            val result = authorizationService.create(
                CreateAuthorizationCommand(
                    tenantId = tenantId,
                    correlationId = correlationId,
                    idempotencyKey = idempotencyKey,
                    patientId = request.patientId.trim(),
                    operatorCode = request.operatorCode.trim(),
                    procedureCodes = request.procedureCodes.map { it.trim() },
                    clinicalJustification = request.clinicalJustification.trim()
                )
            )
            val status = if (result.replayed) Response.Status.OK else Response.Status.CREATED
            Response.status(status).entity(AuthorizationResponse.from(result.authorization)).build()
        } catch (_: IdempotencyConflictException) {
            throw WebApplicationException("Idempotency conflict detected", Response.Status.CONFLICT)
        } catch (_: IdempotencyInProgressException) {
            throw WebApplicationException("Idempotency key is already being processed", Response.Status.CONFLICT)
        }
    }

    @GET
    @Path("/{authorizationId}")
    fun getById(
        @HeaderParam("X-Tenant-Id") tenantIdHeader: String?,
        @PathParam("authorizationId") authorizationId: UUID
    ): AuthorizationResponse {
        val tenantId = parseRequiredUuidHeader("X-Tenant-Id", tenantIdHeader)
        val authorization = authorizationService.getById(tenantId, authorizationId) ?: throw NotFoundException()
        return AuthorizationResponse.from(authorization)
    }

    @POST
    @Path("/{authorizationId}/cancel")
    fun cancel(
        @HeaderParam("X-Tenant-Id") tenantIdHeader: String?,
        @HeaderParam("X-Correlation-Id") correlationIdHeader: String?,
        @PathParam("authorizationId") authorizationId: UUID,
        request: CancelAuthorizationRequest
    ): AuthorizationResponse {
        val tenantId = parseRequiredUuidHeader("X-Tenant-Id", tenantIdHeader)
        val correlationId = parseRequiredUuidHeader("X-Correlation-Id", correlationIdHeader)
        if (request.reason.isBlank()) {
            throw WebApplicationException("reason is required", Response.Status.BAD_REQUEST)
        }
        val authorization = authorizationService.cancel(
            CancelAuthorizationCommand(
                tenantId = tenantId,
                correlationId = correlationId,
                authorizationId = authorizationId,
                reason = request.reason.trim()
            )
        ) ?: throw NotFoundException()
        return AuthorizationResponse.from(authorization)
    }

    private fun parseRequiredUuidHeader(name: String, value: String?): UUID {
        val raw = value?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw WebApplicationException("$name header is required", Response.Status.BAD_REQUEST)
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw WebApplicationException("$name must be a valid UUID", Response.Status.BAD_REQUEST)
        }
    }

    private fun validateCreateRequest(request: CreateAuthorizationRequest) {
        if (request.patientId.isBlank()) {
            throw WebApplicationException("patientId is required", Response.Status.BAD_REQUEST)
        }
        if (request.operatorCode.isBlank()) {
            throw WebApplicationException("operatorCode is required", Response.Status.BAD_REQUEST)
        }
        if (request.procedureCodes.isEmpty() || request.procedureCodes.any { it.isBlank() }) {
            throw WebApplicationException("procedureCodes must contain at least one valid code", Response.Status.BAD_REQUEST)
        }
        if (request.clinicalJustification.isBlank()) {
            throw WebApplicationException("clinicalJustification is required", Response.Status.BAD_REQUEST)
        }
    }
}
