package br.com.autorizasaude.tisshub.operatordispatch.integration

import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapter
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapterSendResult
import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class TypeAOperatorAdapter : OperatorAdapter {
    override val dispatchType: DispatchType = DispatchType.TYPE_A

    override fun send(dispatch: OperatorDispatch): OperatorAdapterSendResult {
        return OperatorAdapterSendResult(
            technicalStatus = TechnicalStatus.ACK_RECEIVED,
            externalProtocol = "A-${UUID.randomUUID().toString().take(8)}"
        )
    }
}
