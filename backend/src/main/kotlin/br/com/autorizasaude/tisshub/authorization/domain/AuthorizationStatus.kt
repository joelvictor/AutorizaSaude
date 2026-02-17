package br.com.autorizasaude.tisshub.authorization.domain

enum class AuthorizationStatus {
    DRAFT,
    VALIDATED,
    DISPATCHED,
    PENDING_OPERATOR,
    AUTHORIZED,
    DENIED,
    CANCELLED,
    EXPIRED,
    FAILED_TECHNICAL
}
