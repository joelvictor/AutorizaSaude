package br.com.autorizasaude.tisshub.api

import br.com.autorizasaude.tisshub.authorization.application.AuthorizationService
import br.com.autorizasaude.tisshub.authorization.application.CreateAuthorizationCommand
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
        request: CreateAuthorizationRequest
    ): Response {
        val tenantId = tenantIdHeader?.let(UUID::fromString)
            ?: throw WebApplicationException("X-Tenant-Id header is required", Response.Status.BAD_REQUEST)

        val authorization = authorizationService.create(
            CreateAuthorizationCommand(
                tenantId = tenantId,
                patientId = request.patientId,
                operatorCode = request.operatorCode,
                procedureCodes = request.procedureCodes,
                clinicalJustification = request.clinicalJustification
            )
        )
        return Response.status(Response.Status.CREATED).entity(AuthorizationResponse.from(authorization)).build()
    }

    @GET
    @Path("/{authorizationId}")
    fun getById(
        @PathParam("authorizationId") authorizationId: UUID
    ): AuthorizationResponse {
        val authorization = authorizationService.getById(authorizationId) ?: throw NotFoundException()
        return AuthorizationResponse.from(authorization)
    }

    @POST
    @Path("/{authorizationId}/cancel")
    fun cancel(
        @PathParam("authorizationId") authorizationId: UUID,
        request: CancelAuthorizationRequest
    ): AuthorizationResponse {
        if (request.reason.isBlank()) {
            throw WebApplicationException("reason is required", Response.Status.BAD_REQUEST)
        }
        val authorization = authorizationService.cancel(authorizationId) ?: throw NotFoundException()
        return AuthorizationResponse.from(authorization)
    }
}
