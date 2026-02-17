package br.com.autorizasaude.tisshub.tissguide.domain

import java.time.OffsetDateTime
import java.util.UUID

data class TissGuide(
    val tissGuideId: UUID,
    val tenantId: UUID,
    val authorizationId: UUID,
    val tissVersion: String,
    val xmlContent: String,
    val xmlHash: String,
    val validationStatus: TissGuideValidationStatus,
    val validationReport: String?,
    val createdAt: OffsetDateTime
)
