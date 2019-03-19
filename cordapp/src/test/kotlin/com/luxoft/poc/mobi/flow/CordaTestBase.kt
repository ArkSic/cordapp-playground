package com.luxoft.poc.mobi.flow

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.FlowStateMachine
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.api.StartedNodeServices
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.newContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CordaTestBase] is the base class for any test that uses mocked Corda mockNet.
 * */
abstract class CordaTestBase {
    /**
     * The mocked Corda mockNet
     * */
    abstract val mockNet: InternalMockNetwork


    /**
     * Substitutes [StartedNodeServices.startFlow] method to run mocked Corda flows.
     *
     * Usage:
     *
     *     val did = store.services.startFlow(GetDidFlow.Initiator(name)).resultFuture.get()
     */
    protected fun <T> StartedNodeServices.startFlow(logic: FlowLogic<T>): FlowStateMachine<T> {
        val machine = startFlow(logic, newContext()).getOrThrow()
        mockNet.runNetwork()

        return object : FlowStateMachine<T> by machine {
            override val resultFuture: CordaFuture<T>
                get() {
                    return machine.resultFuture
                }
        }
    }



    data class StatesOfType<T: ContractState>(val consumed: List<T>, val unconsumed: List<T>)

    inline fun <reified T : ContractState> StartedNode<InternalMockNetwork.MockNode>.statesOfType(): StatesOfType<T> {
        return database.transaction {
            val unconsumed = services.vaultService.queryBy<T>(
                    QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
            )
            val consumed = services.vaultService.queryBy<T>(
                    QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.CONSUMED)
            )

            StatesOfType(
                    consumed = consumed.states.map { it.state.data },
                    unconsumed = unconsumed.states.map { it.state.data }
            )
        }
    }

    fun <T: ContractState> StatesOfType<T>.assertQuantity(consumed: Int = 0, unconsumed: Int = 0): StatesOfType<T> {
        assertEquals(consumed, this.consumed.size)
        assertEquals(unconsumed, this.unconsumed.size)
        return this
    }

    fun <T: ContractState> StatesOfType<T>.singleUnconsumed(): T {
        assertTrue ( consumed.isEmpty() )
        assertEquals(1, unconsumed.size )
        return unconsumed.single()
    }

    fun <T: ContractState> StatesOfType<T>.onlyUnconsumed(): List<T> {
        assertTrue ( consumed.isEmpty() )
        return unconsumed
    }
}