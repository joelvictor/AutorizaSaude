package br.com.autorizasaude.tisshub.operatordispatch.integration

import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapter
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapterSendResult
import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class TypeCOperatorAdapter : OperatorAdapter {
    override val dispatchType: DispatchType = DispatchType.TYPE_C

    override fun send(dispatch: OperatorDispatch): OperatorAdapterSendResult {
        return OperatorAdapterSendResult(
            technicalStatus = TechnicalStatus.SENT,
            externalProtocol = null
        )
    }
}
