package br.com.autorizasaude.tisshub.shared.ids

import java.util.UUID

@JvmInline
value class TenantId(val value: UUID)

@JvmInline
value class AuthorizationId(val value: UUID)
