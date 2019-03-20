package com.luxoft.poc.mobi.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.mobi.*
import com.luxoft.poc.mobi.model.data.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.function.Predicate

/**
 * TODO
 * N.B. In the current implementation the order of elements in [guaranteeIds] must match the order of commitments to be
 * guaranteed !!!
 */
object AcceptOfferFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            private val offer: ProvisionOffer,
            private val guaranteeIds: List<UniqueIdentifier>,
            override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<UniqueIdentifier>() {

        companion object {
            // use minimalistic set of steps
            object COMPOSING: ProgressTracker.Step("Composing the transaction")
            object COLLECTING_SIGS: ProgressTracker.Step("Collecting required signatures") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING: ProgressTracker.Step("Finalising the transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            @JvmStatic
            fun tracker() = ProgressTracker( COMPOSING, COLLECTING_SIGS, FINALISING)
        }

        @Suspendable
        override fun call(): UniqueIdentifier {
            check(ourIdentity == offer.offeree) { "We're not the offeree" }
            val signersKeys = listOf(ourIdentity.owningKey, offer.offeror.owningKey)

            progressTracker.currentStep = COMPOSING
            val commitmentStates = offer.commitments.map {
                when (it) {
                    is ProvisionCommitment<*> -> ProvisionCommitmentState(it)
                    is PaymentCommitment<*> -> PaymentCommitmentState(it)
                    is ActionCommitment<*> -> ActionCommitmentState(it)
                    else -> throw IllegalStateException("Type mismatch: ${Commitment::class.java.canonicalName} expected, but ${it::class.java.canonicalName} found")
                }
            }

            val (guaranteeSnRs, obligationStates) = obligationsToCreateAndGuaranteesToConsume(commitmentStates, guaranteeIds)

            val agreementState = ProvisionAgreementState(
                    provider = offer.offeror,
                    consumer = offer.offeree,
                    commitmentIds = commitmentStates.map { it.linearId },
                    paymentObligationIds = obligationStates.map { it.linearId }
            )

            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                    .addCommand(AcceptOfferCommand(offer, guaranteeIds), signersKeys)
                    // the offering service is a part of the service provider,
                    // so the same keys are used to sign both: the transaction and the command
                    .addCommand(ConfirmOfferCommand(offer), signersKeys)
            guaranteeSnRs.forEach { tx.addInputState(it) }
            commitmentStates.forEach { tx.addOutputState(it, it.contractClassName()) }
            obligationStates.forEach { tx.addOutputState(it, it.contractClassName()) }
            tx.addOutputState(agreementState, agreementState.contractClassName())

            tx.verify(serviceHub)
            val selfSignedTx = serviceHub.signInitialTransaction(tx)

            progressTracker.currentStep = COLLECTING_SIGS

            // the offering service is a part of service provider,
            // so the transaction filtering is just a formal requirement in our case
            val filteredTx = selfSignedTx.buildFilteredTransaction(
                    Predicate { it is Command<*> && it.value is ConfirmOfferCommand }
            )

            val offerorSignature = subFlow(ConfirmOfferFlow.Initiator(offer.offeror, filteredTx))
            val offerorSignedTx = selfSignedTx.withAdditionalSignature(offerorSignature)

            val signedTx = subFlow(
                    CollectSignaturesFlow(
                            offerorSignedTx,
                            listOf(initiateFlow(offer.offeror)),
                            signersKeys,
                            COLLECTING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING
            subFlow(FinalityFlow(signedTx, FINALISING.childProgressTracker()))

            return agreementState.linearId
        }

        private data class GuaranteesAndObligations(
                val guaranteeSnRs: List<StateAndRef<PaymentGuaranteeState>>,
                val obligationStates: List<PaymentObligationState>
        )

        /**
         * TODO
         * N.B. We assume that the order of elements in [guaranteeIds] matches the order of commitments to be
         * guaranteed !!!
         */
        private fun obligationsToCreateAndGuaranteesToConsume(
                commitmentStates: List<CommitmentState<*>>,
                guaranteeIds: List<UniqueIdentifier>
        ) : GuaranteesAndObligations {

            val commitmentsToGuarantee = commitmentStates.filter {
                it is PaymentCommitmentState
                        && it.commitment.details is PostPaymentDetails
                        && (it.commitment.details as PostPaymentDetails).trustedGuarantors.isNotEmpty()
            }

            if (commitmentsToGuarantee.isEmpty()) return GuaranteesAndObligations(emptyList(), emptyList())


            val guaranteeSnRs = serviceHub.vaultService.queryBy<PaymentGuaranteeState>(
                    QueryCriteria.LinearStateQueryCriteria(linearId = guaranteeIds)
            ).states
            check(guaranteeSnRs.size == guaranteeIds.size) { "Some guarantee states are not available" }
            check(commitmentsToGuarantee.size == guaranteeSnRs.size) {
                "Number of guarantees does match number of commitments to be guaranteed"}

            val obligationStates = mutableListOf<PaymentObligationState>()

            for(i in 0 until guaranteeSnRs.size) {
                val commitmentState = commitmentsToGuarantee[i]
                val commitment = commitmentState.commitment as PaymentCommitment<PostPaymentDetails>
                val guarantee = guaranteeSnRs[i].state.data
                check( commitment.amount == guarantee.amount) {
                    "Guaranteed amount ${guarantee.amount} does not match ${commitment.amount}" }
                check(commitment.details.trustedGuarantors.contains(guarantee.guarantor)) {
                    "Guarantor ${guarantee.guarantor} does not belong to the set of trusted guarantors ${commitment.details.trustedGuarantors} "
                }
                obligationStates.add(
                        PaymentObligationState(
                                payer = commitment.performer,
                                guarantor = guarantee.guarantor,
                                beneficiary = commitment.acceptor,
                                reserveId = guarantee.reserveId,
                                commitmentId = commitmentState.linearId
                        )
                )
            }
            return GuaranteesAndObligations(guaranteeSnRs, obligationStates)
        }
    }


    @InitiatedBy(AcceptOfferFlow.Initiator::class)
    class Responder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val signTransactionFlow = object : SignTransactionFlow(session, SignTransactionFlow.tracker()) {
                override fun checkTransaction(stx: SignedTransaction) {

                    // TODO check transaction depending on the role !!!
                }
            }
            subFlow(signTransactionFlow)
        }
    }
}



