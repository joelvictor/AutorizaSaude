package br.com.autorizasaude.tisshub.outbox.api

import br.com.autorizasaude.tisshub.outbox.application.OutboxRelayService
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

data class OutboxStatsResponse(
    val pending: Long,
    val published: Long,
    val deadLetter: Long
)

data class OutboxProcessResponse(
    val scanned: Int,
    val published: Int,
    val failed: Int,
    val deadLettered: Int
)

data class OutboxDeadLetterResponseItem(
    val outboxEventId: Long,
    val eventId: UUID,
    val eventType: String,
    val failureReason: String,
    val failedAt: String
)

data class OutboxDeadLettersResponse(
    val deadLetters: List<OutboxDeadLetterResponseItem>
)

data class OutboxDeadLetterRequeueResponse(
    val requeued: Int
)

@Path("/v1/operations/outbox")
@Produces(MediaType.APPLICATION_JSON)
class OutboxOperationsResource(
    private val outboxRelayService: OutboxRelayService
) {
    @GET
    fun stats(): OutboxStatsResponse {
        val stats = outboxRelayService.stats()
        return OutboxStatsResponse(
            pending = stats.pending,
            published = stats.published,
            deadLetter = stats.deadLetter
        )
    }

    @POST
    @Path("/process")
    fun process(): OutboxProcessResponse {
        val result = outboxRelayService.processPending()
        return OutboxProcessResponse(
            scanned = result.scanned,
            published = result.published,
            failed = result.failed,
            deadLettered = result.deadLettered
        )
    }

    @GET
    @Path("/dead-letters")
    fun deadLetters(
        @HeaderParam("X-Tenant-Id") tenantIdHeader: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int
    ): OutboxDeadLettersResponse {
        val tenantId = parseRequiredUuidHeader("X-Tenant-Id", tenantIdHeader)
        val entries = outboxRelayService.deadLetters(tenantId, limit)
        return OutboxDeadLettersResponse(
            deadLetters = entries.map {
                OutboxDeadLetterResponseItem(
                    outboxEventId = it.outboxEventId,
                    eventId = it.eventId,
                    eventType = it.eventType,
                    failureReason = it.failureReason,
                    failedAt = it.failedAt.toString()
                )
            }
        )
    }

    @POST
    @Path("/dead-letters/requeue")
    fun requeueDeadLetters(
        @HeaderParam("X-Tenant-Id") tenantIdHeader: String?,
        @HeaderParam("X-Correlation-Id") correlationIdHeader: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int
    ): OutboxDeadLetterRequeueResponse {
        val tenantId = parseRequiredUuidHeader("X-Tenant-Id", tenantIdHeader)
        val correlationId = parseRequiredUuidHeader("X-Correlation-Id", correlationIdHeader)
        val result = outboxRelayService.requeueDeadLetters(tenantId, correlationId, limit)
        return OutboxDeadLetterRequeueResponse(requeued = result.requeued)
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
}
