package br.com.autorizasaude.tisshub.operatordispatch.domain

enum class TechnicalStatus {
    READY,
    SENT,
    ACK_RECEIVED,
    POLLING,
    COMPLETED,
    TECHNICAL_ERROR,
    DLQ
}
