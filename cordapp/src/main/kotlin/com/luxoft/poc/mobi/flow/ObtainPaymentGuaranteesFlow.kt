package com.luxoft.poc.mobi.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.mobi.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Requester asks [guarantor] to give him payment guarantees for the given [amounts]. The guarantees are secured by
 * the given [account] owned by the requester and operated by the [guarantor].
 *
 * Initiator party/node role: consumer
 *
 * Responder party/node role: payment operator
 *
 * Returned value: list of [PaymentGuaranteeState.linearId]
 *
 * Ledger updates at guarantor's side: [AccountState.amount] is decremented by sum of the [amounts] and
 * a number of [ReserveState]-s is created -- one per each amount
 *
 * Ledger updates at requester's side: a number of [PaymentGuaranteeState]-s is created -- one per each amount
 *
 * Signers: requester and guarantor
 */
object ObtainPaymentGuaranteesFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            private val guarantor: Party,
            private val account: UniqueIdentifier,
            // TODO !!! replace with something realistic
            private val amounts: List<Int>,
            override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<List<UniqueIdentifier>>() {

        companion object {
            object REQUESTING : ProgressTracker.Step("Requesting payment guarantees")

            @JvmStatic
            fun tracker() = ProgressTracker(REQUESTING)
        }

        @Suspendable
        override fun call(): List<UniqueIdentifier> {
            check(amounts.isNotEmpty()) { "There is nothing to guarantee: the list is empty"}
            amounts.forEach { check(it > 0) { "Each payment amount must be a positive value" } }

            val command = GuaranteePaymentsCommand(
                    requester = ourIdentity,
                    guarantor = guarantor,
                    account = account,
                    amounts = amounts
            )

            progressTracker.currentStep = REQUESTING
            return initiateFlow(guarantor).sendAndReceive<List<UniqueIdentifier>>(command).unwrap { it }
        }
    }

    @InitiatedBy(ObtainPaymentGuaranteesFlow.Initiator::class)
    class Responder(val session: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val command = session.receive<GuaranteePaymentsCommand>().unwrap { it }

            // fetch input account state
            val accountStateAndRef = serviceHub.vaultService.queryBy<AccountState>(
                    QueryCriteria.LinearStateQueryCriteria(linearId = listOf(command.account))
            ).states.single()

            // calc output account state
            val accountState = accountStateAndRef.state.data.copy(
                    amount = accountStateAndRef.state.data.amount - command.amounts.sum()
            )

            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                    .addCommand(command, ourIdentity.owningKey, command.requester.owningKey)
                    .addInputState(accountStateAndRef)
                    .addOutputState(accountState, accountState.contractClassName())

            // create reserves and guarantees
            val guarantees = mutableListOf<UniqueIdentifier>()
            for (amount in command.amounts) {
                val reserveState = ReserveState(
                        amount = amount,
                        account = command.account,
                        participants = listOf(ourIdentity))
                tx.addOutputState(reserveState, reserveState.contractClassName())

                val paymentGuaranteeState = PaymentGuaranteeState(
                        requester = command.requester,
                        guarantor = ourIdentity,
                        amount = amount,
                        reserveId = reserveState.linearId
                )
                tx.addOutputState(paymentGuaranteeState, paymentGuaranteeState.contractClassName())
                guarantees.add(paymentGuaranteeState.linearId)
            }

            tx.verify(serviceHub)
            val selfSignedTx = serviceHub.signInitialTransaction(tx)
            val signedTx = subFlow(GetCounterpartySignatureFlow(selfSignedTx, session.counterparty))
            subFlow(FinalityFlow(signedTx))

            session.send(guarantees)
        }
    }
}



/**
 * "Callback" flow: the guarantor has composed the transaction and asks counterparty to sign that one.
 */
@InitiatingFlow
class GetCounterpartySignatureFlow(
        private val selfSignedTx: SignedTransaction,
        private val counterparty: Party
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(CollectSignaturesFlow(selfSignedTx, listOf(initiateFlow(counterparty))))
    }
}

/**
 * Requester makes sure that (1) all requested guarantees are obtained and (2) his account is not robbed.
 * Then the requester signs the transaction.
 */
@InitiatedBy(GetCounterpartySignatureFlow::class)
class GetCounterpartySignatureResponder(val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val flow = object: SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {
                stx.verify(serviceHub, checkSufficientSignatures = false)
                stx.checkSignaturesAreValid()
            }
        }
        subFlow(flow)
    }
}