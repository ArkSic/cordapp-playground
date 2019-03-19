package com.luxoft.poc.mobi.flow

import com.luxoft.poc.mobi.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.MockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class FlowTests : CordaTestBase() {
    override lateinit var mockNet: InternalMockNetwork

    private lateinit var notary: Party
    private lateinit var consumer: Party
    private lateinit var paymentOperator1: Party
    private lateinit var paymentOperator2: Party
    private lateinit var airlineProvider: Party
    private lateinit var bikeProvider: Party
    private lateinit var carProvider: Party
    private lateinit var ferryProvider: Party
    private lateinit var taxiProvider: Party
    private lateinit var trainProvider: Party


    private lateinit var notaryNode: StartedNode<MockNode>
    private lateinit var consumerNode: StartedNode<MockNode>
    private lateinit var paymentOperator1Node: StartedNode<MockNode>
    private lateinit var paymentOperator2Node: StartedNode<MockNode>
    private lateinit var airlineProviderNode: StartedNode<MockNode>
    private lateinit var bikeProviderNode: StartedNode<MockNode>
    private lateinit var carProviderNode: StartedNode<MockNode>
    private lateinit var ferryProviderNode: StartedNode<MockNode>
    private lateinit var taxiProviderNode: StartedNode<MockNode>
    private lateinit var trainProviderNode: StartedNode<MockNode>

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(listOf(
                "com.luxoft.poc.mobi",
                "com.luxoft.poc.mobi.flow",
                "com.luxoft.poc.mobi.service"
        // TODO
        ))

        notaryNode = mockNet.defaultNotaryNode
        consumerNode = mockNet.createPartyNode(X500Names.Consumer)
        paymentOperator1Node = mockNet.createPartyNode(X500Names.MoneyLender1)
        paymentOperator2Node = mockNet.createPartyNode(X500Names.MoneyLender2)
        airlineProviderNode = mockNet.createPartyNode(X500Names.AirlineProvider)
        bikeProviderNode = mockNet.createPartyNode(X500Names.BikeProvider)
        carProviderNode = mockNet.createPartyNode(X500Names.CarProvider)
        ferryProviderNode = mockNet.createPartyNode(X500Names.FerryProvider)
        taxiProviderNode = mockNet.createPartyNode(X500Names.TaxiProvider)
        trainProviderNode = mockNet.createPartyNode(X500Names.TrainProvider)

        notary = notaryNode.info.singleIdentity()
        consumer = consumerNode.info.singleIdentity()
        paymentOperator1 = paymentOperator1Node.info.singleIdentity()
        paymentOperator2 = paymentOperator2Node.info.singleIdentity()
        airlineProvider = airlineProviderNode.info.singleIdentity()
        bikeProvider = bikeProviderNode.info.singleIdentity()
        carProvider = carProviderNode.info.singleIdentity()
        ferryProvider = ferryProviderNode.info.singleIdentity()
        taxiProvider = taxiProviderNode.info.singleIdentity()
        trainProvider = trainProviderNode.info.singleIdentity()

        val serviceProviders = listOf(
                airlineProviderNode,
                bikeProviderNode,
                carProviderNode,
                ferryProviderNode,
                taxiProviderNode,
                trainProviderNode
        )

        val paymentOperators = listOf(
                paymentOperator1Node,
                paymentOperator2Node
        )

        paymentOperators.forEach {
            it.registerInitiatedFlow(MockInitAccountFlow.Responder::class.java)
            it.registerInitiatedFlow(ObtainPaymentGuaranteesFlow.Responder::class.java)
            // TODO
        }

        serviceProviders.forEach {
            it.registerInitiatedFlow(CollectOffersFlow.Responder::class.java)
            it.registerInitiatedFlow(ConfirmOfferFlow.Responder::class.java)
            it.registerInitiatedFlow(AcceptOfferFlow.Responder::class.java)
            // TODO
        }
    }

    @After
    fun tearDown() = mockNet.stopNodes()

    @Test
    fun createSingleAccountTest() {
        val initialAmount = 1000
        val operator = paymentOperator1
        val accountId = consumerNode.services.startFlow(MockInitAccountFlow.Initiator(
                operator, initialAmount)).resultFuture.getOrThrow()

        val accountState = paymentOperator1Node.statesOfType<AccountState>().unconsumed.single()
        assert(accountState.linearId == accountId)
        assert(accountState.amount == initialAmount)
        assert(accountState.owner == consumer)
    }

    @Test
    fun createMultiAccountTest() {
        data class AccountInfo(
                val operator: Party,
                val operatorNode: StartedNode<InternalMockNetwork.MockNode>,
                val initialAmount: Int,
                var accountId: UniqueIdentifier? = null
        )

        val accountInfos = listOf(
                AccountInfo(paymentOperator1, paymentOperator1Node, 1000),
                AccountInfo(paymentOperator2, paymentOperator2Node, 1100),
                AccountInfo(paymentOperator1, paymentOperator1Node, 1200),
                AccountInfo(paymentOperator2, paymentOperator2Node, 1300)
        )

        for (accountInfo in accountInfos)
            accountInfo.accountId = consumerNode.services.startFlow(
                    MockInitAccountFlow.Initiator(accountInfo.operator, accountInfo.initialAmount)
            ).resultFuture.getOrThrow()

        val unconsumedAccountStatesByOperator = mapOf(
                Pair(paymentOperator1, paymentOperator1Node.statesOfType<AccountState>().unconsumed),
                Pair(paymentOperator2, paymentOperator2Node.statesOfType<AccountState>().unconsumed)
        )

        assert(unconsumedAccountStatesByOperator.values.sumBy { it.size } == accountInfos.size)

        for (accountInfo in accountInfos) {
            val accountState = unconsumedAccountStatesByOperator[accountInfo.operator]!!
                    .find { it.linearId == accountInfo.accountId }!!
            assert(accountState.owner == consumer)
            assert(accountState.amount == accountInfo.initialAmount)
        }
    }

    @Test
    fun guaranteePaymentsFlowTestPositive() {
        val creditLimit = 100000
        val operator = paymentOperator1
        val amounts = listOf(111, 222, 333, 444, 555, 666)
//        val amountSet = amounts.toSet()
//        val total = amounts.sum()
        // init account
        val accountId = consumerNode.services
                .startFlow(MockInitAccountFlow.Initiator(operator, creditLimit))
                .resultFuture.getOrThrow()
        // request guarantees
        val guaranteeIds = consumerNode.services
                .startFlow(ObtainPaymentGuaranteesFlow.Initiator(operator, accountId, amounts))
                .resultFuture.getOrThrow()

        assert(guaranteeIds.size == amounts.size)
        // check ...
//        val accountStates = listOf(
//                paymentOperator1Node.statesOfType<AccountState>().unconsumed.single(),
//                consumerNode.statesOfType<AccountState>().unconsumed.single()
//        )
//        for (accountState in accountStates) {
//            assert(accountState.reservedAmounts.size == amounts.size)
//            assert(accountState.reservedAmounts.sum() == total)
//            assert(accountState.amount == creditLimit - total)
//        }
        val guarantees = consumerNode.statesOfType<PaymentGuaranteeState>()
                .assertQuantity(consumed = 0, unconsumed = amounts.size)
        paymentOperator1Node.statesOfType<PaymentGuaranteeState>().assertQuantity(consumed = 0, unconsumed = 0)

        consumerNode.statesOfType<ReserveState>().assertQuantity(consumed = 0, unconsumed = 0)
        val reserves = paymentOperator1Node.statesOfType<ReserveState>()
                .assertQuantity(consumed = 0, unconsumed = amounts.size)
    }

    // TODO
    @Test
    fun revokeFlowTestPositive() {
        val creditLimit = 100000
        val operator = paymentOperator1
        val amounts = listOf(111, 222, 333, 444, 555, 666)
        val amountSet = amounts.toSet()
        val total = amounts.sum()
        // init account
        val accountId = consumerNode.services
                .startFlow(MockInitAccountFlow.Initiator(operator, creditLimit))
                .resultFuture.getOrThrow()
        // request guarantees
        val guaranteeIds = consumerNode.services
                .startFlow(ObtainPaymentGuaranteesFlow.Initiator(operator, accountId, amounts))
                .resultFuture.getOrThrow()
        // check ...
//        val accountStates = listOf(
//                paymentOperator1Node.statesOfType<AccountState>().unconsumed.single(),
//                consumerNode.statesOfType<AccountState>().unconsumed.single()
//        )
//        for (accountState in accountStates) {
//            assert(accountState.reservedAmounts.size == amounts.size)
//            assert(accountState.reservedAmounts.sum() == total)
//            assert(accountState.amount == creditLimit - total)
//        }
        val guarantees = consumerNode.statesOfType<PaymentGuaranteeState>()
                .assertQuantity(consumed = 0, unconsumed = amounts.size)
        paymentOperator1Node.statesOfType<PaymentGuaranteeState>().assertQuantity(consumed = 0, unconsumed = 0)

        consumerNode.statesOfType<ReserveState>().assertQuantity(consumed = 0, unconsumed = 0)
        val reserves = paymentOperator1Node.statesOfType<ReserveState>()
                .assertQuantity(consumed = 0, unconsumed = amounts.size)

        consumerNode.services.startFlow(RevokePaymentGuaranteesFlow(paymentOperator1, guaranteeIds))

        consumerNode.statesOfType<PaymentGuaranteeState>().assertQuantity(consumed = amounts.size, unconsumed = 0)
        consumerNode.statesOfType<ReserveState>().assertQuantity(consumed = 0, unconsumed = 0)
        consumerNode.statesOfType<AccountState>().assertQuantity(consumed = 0, unconsumed = 0)

        paymentOperator1Node.statesOfType<PaymentGuaranteeState>().assertQuantity(consumed = 0, unconsumed = 0)
        paymentOperator1Node.statesOfType<ReserveState>().assertQuantity(consumed = amounts.size, unconsumed = 0)
        paymentOperator1Node.statesOfType<AccountState>().assertQuantity(consumed = 2, unconsumed = 1)
    }


// TODO positive tests different guarantors
// TODO negative test = wrong guarantor
// TODO negative test = short of money

    @Test
    fun collectOffersFlowTest() {
        val dummyCommitment: ProvisionCommitment<ProvisionDetails> = ProvisionCommitment(airlineProvider, consumer, TripProvisionDetails(
                from = GeoPoints.SPB,
                to = GeoPoints.HEL,
                departAfter = LocalDateTime.now(),
                arriveBefore = LocalDateTime.now().plusWeeks(1),
                transportType = TransportType.AIRLINER
        ))
        val offers = consumerNode.services.startFlow(CollectOffersFlow.Initiator(dummyCommitment)).resultFuture.getOrThrow()
//        assert(offers.size == 1)
//        assert(offers[0].commitments[0] == dummyCommitment)
    }

    @Test
    fun acceptOfferFlow() {
        val creditLimit = 100000
        val operator = paymentOperator1
        val accountId = consumerNode.services
                .startFlow(MockInitAccountFlow.Initiator(operator, creditLimit))
                .resultFuture.getOrThrow()

        val dummyCommitment: ProvisionCommitment<ProvisionDetails> = ProvisionCommitment(airlineProvider, consumer, TripProvisionDetails(
                from = GeoPoints.SPB,
                to = GeoPoints.HEL,
                departAfter = LocalDateTime.now().plusDays(1).withHour(11).withMinute(11).withSecond(11).withNano(0),
                arriveBefore = LocalDateTime.now().plusDays(2).withHour(23).withMinute(59).withSecond(59).withNano(0),
                transportType = TransportType.AIRLINER
        ))
        val offers = consumerNode.services.startFlow(CollectOffersFlow.Initiator(dummyCommitment)).resultFuture.getOrThrow()
        // find the first offer with at least one guaranteed payment commitment
//        val offer = offers.filter { it.commitments.filter { it is PaymentCommitment && it.details is PostPaymentDetails && (it.details as PostPaymentDetails).trustedGuarantors.isNotEmpty() }.isNotEmpty() }.first()
        val offer = offers.first()

        val guaranteedPayments = offer.commitments.filter {
                it is PaymentCommitment
                && it.details is PostPaymentDetails
                && (it.details as PostPaymentDetails).trustedGuarantors.isNotEmpty()
        } as List<PaymentCommitment<PostPaymentDetails>>


        val guaranteeIds = if (guaranteedPayments.isNotEmpty()) {
            val amounts = guaranteedPayments.map { it.amount }
            consumerNode.services.startFlow(ObtainPaymentGuaranteesFlow.Initiator(paymentOperator1, accountId, amounts)).resultFuture.getOrThrow()
        } else emptyList<UniqueIdentifier>()
        val agreementId = consumerNode.services.startFlow(AcceptOfferFlow.Initiator(offer, guaranteeIds)).resultFuture.getOrThrow()
        val agreementStates = consumerNode.statesOfType<ProvisionAgreementState>().assertQuantity(consumed = 0, unconsumed = 1)
        val commitmentStates = consumerNode.statesOfType<CommitmentState<CommitmentDetails>>().assertQuantity(consumed = 0, unconsumed = offer.commitments.size)
    }
}