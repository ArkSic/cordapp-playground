package com.luxoft.poc.mobi.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.mobi.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
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
            object SIGNING: ProgressTracker.Step("Verifying and signing payment operator's feedback")
            @JvmStatic
            fun tracker() = ProgressTracker( REQUESTING, SIGNING )
        }

        @Suspendable
        override fun call(): List<UniqueIdentifier> {
            check(amounts.isNotEmpty()) { "There is nothing to guarantee: the list is empty"}
            amounts.forEach { check(it > 0) { "Each payment amount must be a positive value" } }

            progressTracker.currentStep = REQUESTING
            val session = initiateFlow(guarantor)
            session.send(GuaranteePaymentsCommand(account = account, amounts = amounts))

            progressTracker.currentStep = SIGNING
            var result = listOf<UniqueIdentifier>()
            val signFlow = object: SignTransactionFlow(session) {
                @Suspendable override fun checkTransaction(stx: SignedTransaction) {
                    val guaranteeStates = stx.coreTransaction.outputsOfType<PaymentGuaranteeState>()
                    // TODO verify that all the guaranties are there (in the proper order) and our account has not been robbed
                    result = guaranteeStates.map { it.linearId }
                }
            }
            subFlow(signFlow)

            return result
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

            // calc output account state's amount
            val accountState = accountStateAndRef.state.data.copy(
                    amount = accountStateAndRef.state.data.amount - command.amounts.sum())

            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                    .addCommand(command, ourIdentity.owningKey, session.counterparty.owningKey)
                    .addInputState(accountStateAndRef)
                    .addOutputState(accountState, accountState.contractClassName())

            // create reserves and guarantees
            for (amount in command.amounts) {
                val reserveState = ReserveState(
                        amount = amount,
                        account = command.account,
                        participants = listOf(ourIdentity))
                tx.addOutputState(reserveState, reserveState.contractClassName())

                val guaranteeState = PaymentGuaranteeState(
                        requester = session.counterparty,
                        guarantor = ourIdentity,
                        amount = amount,
                        reserveId = reserveState.linearId
                )
                tx.addOutputState(guaranteeState, guaranteeState.contractClassName())
            }

            tx.verify(serviceHub)
            val selfSignedTx = serviceHub.signInitialTransaction(tx)
            // obtain requester's signature
            val signedTx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(session)))

            subFlow(FinalityFlow(signedTx))
        }
    }
}
