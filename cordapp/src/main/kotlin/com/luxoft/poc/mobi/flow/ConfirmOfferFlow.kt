package com.luxoft.poc.mobi.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.mobi.model.data.ProvisionOffer
import com.luxoft.poc.mobi.service.ProvisionOfferingService
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap

/**
 * Sub-flow: consumer asks the provider [offeror] to confirm that the given [ProvisionOffer] has been originated by its
 * [ProvisionOfferingService] and it has not been expired yet. The provider leverages [ProvisionOfferingService.sign]
 * to check this fact and to sign [filteredTx].
 */
object ConfirmOfferFlow {

    @InitiatingFlow
    class Initiator(
            val offeror: Party,
            val filteredTx: FilteredTransaction
    ) : FlowLogic<TransactionSignature>() {

        @Suspendable
        override fun call(): TransactionSignature =
                initiateFlow(offeror).sendAndReceive<TransactionSignature>(filteredTx).unwrap { it }
    }

    @InitiatedBy(ConfirmOfferFlow.Initiator::class)
    class Responder(
            private val session: FlowSession
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val request = session.receive<FilteredTransaction>().unwrap { it }
            val response = serviceHub.cordaService(ProvisionOfferingService::class.java).sign(request)
            session.send(response)
        }
    }
}

