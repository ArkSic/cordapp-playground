package com.luxoft.poc.mobi.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.mobi.AccountState
import com.luxoft.poc.mobi.MockInitAccountCommand
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * TODO
 * N.B. For demos and tests only: this flow creates money out from the air.
 */
object MockInitAccountFlow {

    // TODO @StartableByRPC && ProgressTracker ???
    @InitiatingFlow
    class Initiator(
            private val operator: Party,
            private val initialAmount: Int
    ) : FlowLogic<UniqueIdentifier>() {

        @Suspendable
        override fun call(): UniqueIdentifier {

            val command = MockInitAccountCommand(
                    owner = ourIdentity,
                    operator = operator,
                    initialAmount = initialAmount
            )

            return initiateFlow(operator)
                    .sendAndReceive<UniqueIdentifier>(command)
                    .unwrap { it }
        }
    }

    @InitiatedBy(MockInitAccountFlow.Initiator::class)
    class Responder(
            private val session: FlowSession
    ) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val command = session.receive<MockInitAccountCommand>().unwrap { it }
            assert(command.operator == ourIdentity)
            assert(command.initialAmount > 0)

            val accountState = AccountState(
                    owner = command.owner,
                    amount = command.initialAmount,
                    participants = listOf(ourIdentity)
            )

            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                    .addCommand(command, ourIdentity.owningKey)
                    .addOutputState(accountState, accountState.contractClassName())

            tx.verify(serviceHub)
            val signedTx = serviceHub.signInitialTransaction(tx)
            subFlow(FinalityFlow(signedTx))

            session.send(accountState.linearId)
        }
    }
}
