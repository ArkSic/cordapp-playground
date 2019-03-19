package com.luxoft.poc.mobi.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.mobi.AccountState
import com.luxoft.poc.mobi.PaymentGuaranteeState
import com.luxoft.poc.mobi.ReserveState
import com.luxoft.poc.mobi.RevokePaymentGuaranteesCommand
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class RevokePaymentGuaranteesFlow(
        private val guarantor: Party,
        private val guarantees: List<UniqueIdentifier>,
        override val progressTracker: ProgressTracker = tracker()
) : FlowLogic<Unit>() {
    companion object {
        // TODO
        @JvmStatic
        fun tracker() = ProgressTracker()
    }

    @Suspendable
    override fun call() {
        check(guarantees.isNotEmpty()) { "There is nothing to revoke: the list is empty" }
        // fetch guarantees states
        val guaranteeStatesAndRefs = serviceHub.vaultService.queryBy<PaymentGuaranteeState>(
                QueryCriteria.LinearStateQueryCriteria(linearId = guarantees)
        ).states

        check(guarantees.size == guaranteeStatesAndRefs.size) { "Some guarantees have been already consumed" }

        initiateFlow(guarantor).sendAndReceive<Unit>(guaranteeStatesAndRefs)
    }
}

@InitiatedBy(RevokePaymentGuaranteesFlow::class)
class RevokePaymentGuaranteesResponder(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val guaranteeStatesAndRefs = session.receive<List<StateAndRef<PaymentGuaranteeState>>>().unwrap { it }

        val reserves = guaranteeStatesAndRefs.map { it.state.data.reserveId }
        // fetch reserveId states
        val reserveStatesAndRefs = serviceHub.vaultService.queryBy<ReserveState>(
                QueryCriteria.LinearStateQueryCriteria(linearId = reserves)
        ).states

        check(reserves.size == reserveStatesAndRefs.size)
                { "Some of the reserves are not available -- already consumed?" }

        // we need to support "mixed" mode -- if the guarantees are secured by different accounts
        val reservesByAccount = reserveStatesAndRefs.groupBy { it.state.data.account }
        val amountsByAccount = reservesByAccount.mapValues { it.value.sumBy { it.state.data.amount } }

        // fetch account states
        val accountStatesAndRefs = serviceHub.vaultService.queryBy<AccountState>(
                QueryCriteria.LinearStateQueryCriteria(linearId = reservesByAccount.keys.toList())
        ).states

        check(reservesByAccount.keys.size == accountStatesAndRefs.size)
                { "Some of the accounts are not available -- already consumed?"}

        val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
        // consume all the guarantees and all the reserves
        guaranteeStatesAndRefs.forEach { tx.addInputState(it) }
        reserveStatesAndRefs.forEach { tx.addInputState(it) }
        // return previously reserved amounts to the corresponding accounts
        accountStatesAndRefs.forEach { tx.addInputState(it) }
        accountStatesAndRefs.forEach {
            val accountState = it.state.data
            val newAmount = accountState.amount + amountsByAccount[accountState.linearId]!!
            tx.addOutputState(accountState.copy(amount = newAmount), accountState.contractClassName())
        }
        tx.addCommand(RevokePaymentGuaranteesCommand(), ourIdentity.owningKey, session.counterparty.owningKey)

        tx.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(tx)
        val signedTx = subFlow(GetCounterpartySignatureFlow(selfSignedTx, session.counterparty))
        subFlow(FinalityFlow(signedTx))

        session.send(Unit)
    }
}