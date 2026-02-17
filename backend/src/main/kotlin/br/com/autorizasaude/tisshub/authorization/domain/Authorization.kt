package br.com.autorizasaude.tisshub.authorization.domain

import java.time.OffsetDateTime
import java.util.UUID

data class Authorization(
    val authorizationId: UUID,
    val tenantId: UUID,
    val patientId: String,
    val operatorCode: String,
    val procedureCodes: List<String>,
    val clinicalJustification: String,
    val status: AuthorizationStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
