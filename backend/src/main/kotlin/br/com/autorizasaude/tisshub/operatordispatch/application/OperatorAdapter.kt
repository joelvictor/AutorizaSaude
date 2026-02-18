package br.com.autorizasaude.tisshub.operatordispatch.application

import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus

enum class ExternalAuthorizationStatus {
    PENDING,
    APPROVED,
    DENIED
}

data class OperatorAdapterSendResult(
    val technicalStatus: TechnicalStatus,
    val externalProtocol: String?
)

data class OperatorAdapterPollResult(
    val externalStatus: ExternalAuthorizationStatus,
    val operatorReference: String?,
    val denialReasonCode: String?,
    val denialReason: String?
)

interface OperatorAdapter {
    val dispatchType: DispatchType
    fun send(dispatch: OperatorDispatch): OperatorAdapterSendResult
    fun poll(dispatch: OperatorDispatch): OperatorAdapterPollResult
}
