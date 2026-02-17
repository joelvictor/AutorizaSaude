package br.com.autorizasaude.tisshub.operatordispatch.application

import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus

data class OperatorAdapterSendResult(
    val technicalStatus: TechnicalStatus,
    val externalProtocol: String?
)

interface OperatorAdapter {
    val dispatchType: DispatchType
    fun send(dispatch: OperatorDispatch): OperatorAdapterSendResult
}
