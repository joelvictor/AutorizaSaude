package br.com.autorizasaude.tisshub.outbox.api

import br.com.autorizasaude.tisshub.outbox.application.OutboxRelayService
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

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
}
