package com.luxoft.poc.mobi.service

import com.luxoft.poc.mobi.ConfirmOfferCommand
import com.luxoft.poc.mobi.ProvisionCommitment
import com.luxoft.poc.mobi.ProvisionDetails
import com.luxoft.poc.mobi.ProvisionOffer
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import kotlin.IllegalArgumentException
import kotlin.reflect.jvm.javaConstructor

/**
 * Oracle service
 * * originates [ProvisionOffer]-s satisfying the given [ProvisionCommitment]
 * * confirms (or disproves) that the given [ProvisionOffer] offer has been originated by this service and has not been
 * expired yet.
 *
 * The service uses external data sources (see [ProvisionOfferDS]) to compose [ProvisionOffer]-s.
 * Specific [ProvisionOfferDS] class is specified by "ProvisionOfferDS" property in the node's configuration file.
 * The configuration file may also contain some properties to configure specific [ProvisionOfferDS] --
 * e.g. some IP address, DB or RPC username and password, etc. All these properties are passed to [ProvisionOfferDS]
 * constructor and should be interpreted (or ignored) by specific data source logic.
 *
 * For testing (and, possibly, for demo) we use a number mocks of trip-provision data sources (currently these are
 * [MockAirlineProvisionDS], [MockBikeProvisionDS], [MockCarProvisionDS], [MockFerryProvisionDS],
 * [MockTaxiProvisionDS] and [MockTrainProvisionDS]). These data sources generate predictable set of typical offers
 * and may require some specific configuration properties -- see [MockTripProvisionDS] for more deteils.
 */
@CordaService
class ProvisionOfferingService(
        private val services: ServiceHub
) : SingletonSerializeAsToken() {

    private val myKey = services.myInfo.legalIdentities.first().owningKey
    private val properties by lazy { loadConfigProperties(services.myInfo.legalIdentities.first().name) }
    private val dataSource by lazy { configureDataSource() }
    private val verificationCache = VerificationCache()

    /**
     * Originates [ProvisionOffer]-s satisfying the given [ProvisionCommitment]
     */
    fun query(requestedCommitment: ProvisionCommitment<ProvisionDetails>) : List<ProvisionOffer> =
            verificationCache.updateWith(dataSource.composeOffers(requestedCommitment))

    /**
     * Confirms (or disproves) if the given [ProvisionOffer] offer has been originated by this service and has not been
     * expired yet.
     */
    fun sign(ftx: FilteredTransaction) : TransactionSignature {
        ftx.verify()
        val okay = ftx.checkWithFun {
            it is Command<*>
                    && it.value is ConfirmOfferCommand
                    && myKey in it.signers
                    && verificationCache.contains((it.value as ConfirmOfferCommand).offer)
        }

        if (okay) return services.createSignature(ftx, myKey)

        throw IllegalArgumentException("Signature requested over invalid transaction")
    }

    /**
     * Loads configuration properties from accessible property file. The file name may be either default
     * "node.properties" or node-specific (see below). Node-specific file names work for both "real" and mock networks,
     * default file name works for "real" network only.
     *
     * The following naming conventions stand for node-specific configuration file:
     * * file extension is ".properties"
     * * the name contains sequence of components of X500Name (CN,OU,O,L,C) associated with the given node. Components
     * should be separated by "-" (dash) character;
     * * if some X500Name component is omitted (is null), an empty string is used;
     * * if some X500Name component contains non-alphanumeric characters, these characters should be replaced with "_"
     * (underscores)
     *
     * If both configuration files are present, node-specific file is used.
     *
     * TODO: could we move it to some common lib ??? -- this approach may appear useful
     */
    private fun loadConfigProperties(ourName: CordaX500Name): Properties {
        val props = Properties()
        // match any non-alphanumeric character
        // N.B. Kotlin does not recognize strings as regex-s implicitly, so we need do declare this one explicitly
        val regex = "\\W".toRegex()
        val commonName = ourName.commonName?.replace(regex , "_") ?: ""
        val orgUnit = ourName.organisationUnit?.replace(regex, "_") ?: ""
        val org = ourName.organisation.replace(regex, "_")
        val loc = ourName.locality.replace(regex, "_")
        // country code never contains non-alphanumeric characters, so it is used "as is"
        val cfgFileName = "${commonName}-${orgUnit}-${org}-${loc}-${ourName.country}"
        val cfgPath = Paths.get("${cfgFileName}.properties").toAbsolutePath()
        val defaultCfgPath = Paths.get("node.properties").toAbsolutePath()
        val reader =  try {
            // try to read node-specific config first
            Files.newBufferedReader(cfgPath)
        }  catch(e: IOException) {
            try {
                // hard luck: try to read general config
                Files.newBufferedReader(defaultCfgPath)
            } catch (e: IOException) {
                // epic fail: we cannot read any config ... give up
                throw IllegalStateException("""
                    |Failed to open configuration file:
                    |    neither ${cfgPath}
                    |    nor ${defaultCfgPath}
                    |is accessible.
                """.trimIndent(), e)
            }
        }
        reader.use {
            props.load(reader)
        }
        return props
    }

    /**
     * Instantiates data source class. Passes node properties to its constructor, so data source class decides,
     * which properties are appropriate and should be interpreted and which ones should be silently ignored.
     */
    private fun configureDataSource(): ProvisionOfferDS {
        val className = properties.getProperty("ProvisionOfferDS")
        val kClass = Class.forName(className).kotlin
        val constructor = kClass.constructors.first()
        return constructor.javaConstructor?.newInstance(properties, services) as ProvisionOfferDS
    }

    /**
     * [VerificationCache] keeps track of all valid [ProvisionOffer]-s originated by the given
     * [ProvisionOfferingService]. Each the offer is represented by its hash. Hashes are grouped/sorted
     * by those expiration time. Basic operation flow assumes the following order of operation:
     * * when the service composes new bunch of [ProvisionOffer]-s, hashes of these offers are placed
     * into the cache -- see [VerificationCache.updateWith] method;
     * * when [ProvisionOffer] verification is requested, we remove expired entries from the cache
     * and then check that the cache still contains hash of this [ProvisionOffer] -- see [VerificationCache.contains]
     * method.
     *
     * TODO: replace primitive Int hash with something more robust like MD or SHA
     */
    private class VerificationCache {

        /**
         * The cache: sets of [ProvisionOffer] hashes are grouped by expiration time, i.e. by [ProvisionOffer.validBefore].
         * In order to keep number of buckets reasonable we round-down the expiration time to a whole number of seconds.
         */
        private val hashesByExpTime = sortedMapOf<LocalDateTime, MutableSet<Int>>()

        /**
         * Adds hashes of the listed [ProvisionOffer]-s to this [VerificationCache].
         * Returns its argument (list of [ProvisionOffer]-s)
         */
        fun updateWith(provisionOffers: List<ProvisionOffer>): List<ProvisionOffer> {
            // while we do not need to purge expired entries here, that helps to keep the cache as small
            // as possible, especially if the service clients frequently query, but rarely sign
            purgeExpiredEntries()
            provisionOffers.forEach {
                val key = it.validBefore.withNano(0)
                val hash = it.hashCode()
                synchronized(hashesByExpTime) {
                    hashesByExpTime[key]?.add(hash) ?: hashesByExpTime.put(key, mutableSetOf(hash))
                }
            }
            return provisionOffers
        }

        /**
         * Proves (or disproves) that the given [ProvisionOffer] has been originated by this service and has not been
         * expired yet. In the context of the current implementation, it means that (1) the cache contains valid
         * (unexpired) entries ONLY and (2) [hashCode] of the given [ProvisionOffer] presents in the cache.
         */
        fun contains(offer: ProvisionOffer): Boolean {
            purgeExpiredEntries()
            // round-down the expiration time to a whole number of seconds
            val key =  offer.validBefore.withNano(0)
            synchronized(hashesByExpTime) {
                return hashesByExpTime[key]
                        ?.contains(offer.hashCode())
                        ?: false
            }
        }

        /**
         * Removes all expired entries.
         */
        private fun purgeExpiredEntries() {
            // keys have been down-rounded, lets down-round our time too -- that gives the customer one last chance
            val now = LocalDateTime.now().withNano(0)
            synchronized(hashesByExpTime) {
                // N.B. the key set is sorted, so we retrieve the oldest entries first
                for (key in hashesByExpTime.keys)
                    if (key.isBefore(now)) hashesByExpTime.remove(key)
                    else break
            }
        }
    }
}


