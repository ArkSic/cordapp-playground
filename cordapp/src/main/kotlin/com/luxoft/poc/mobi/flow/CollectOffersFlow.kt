package com.luxoft.poc.mobi.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.mobi.ProvisionCommitment
import com.luxoft.poc.mobi.ProvisionDetails
import com.luxoft.poc.mobi.ProvisionOffer
import com.luxoft.poc.mobi.service.ProvisionOfferingService
import net.corda.core.flows.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * A consumer asks specific provider to list possible [ProvisionOffer]-s for the given [ProvisionCommitment],
 * where the consumer should act as the [ProvisionCommitment.acceptor] and the provider should act as the
 * [ProvisionCommitment.performer]. The provider leverages [ProvisionOfferingService.query] to compose requested
 * offers and sends results to the consumer.
 *
 * Initiator: node of [ProvisionCommitment.acceptor]
 *
 * Input(s): the [ProvisionCommitment]
 *
 * Responder: node of [ProvisionCommitment.performer]
 *
 * Output: possibly empty list of [ProvisionOffer]-s
 *
 * Ledger updates: NONE (oracle service)
 */
object CollectOffersFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            private val commitment: ProvisionCommitment<ProvisionDetails>,
            override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<List<ProvisionOffer>>() {

        companion object {
            // use minimalistic set of steps
            object QUERYING: ProgressTracker.Step("Querying offers from the provider ...")
            @JvmStatic
            fun tracker() = ProgressTracker( QUERYING )
        }

        @Suspendable
        override fun call(): List<ProvisionOffer> {
            assert(ourIdentity == commitment.acceptor)

            progressTracker.currentStep = QUERYING
            return initiateFlow(commitment.performer).sendAndReceive<List<ProvisionOffer>>(commitment).unwrap { it }
        }
    }

    @InitiatedBy(CollectOffersFlow.Initiator::class)
    class Responder(private val session: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val request = session.receive<ProvisionCommitment<ProvisionDetails>>().unwrap { it }
            val response = serviceHub.cordaService(ProvisionOfferingService::class.java).query(request)
            session.send(response)
        }
    }
}


