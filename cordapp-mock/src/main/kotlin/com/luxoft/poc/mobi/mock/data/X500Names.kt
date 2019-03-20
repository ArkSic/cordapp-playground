package com.luxoft.poc.mobi.mock.data

import net.corda.core.identity.CordaX500Name

/**
 * X500 names for nodes parties -- for testing and (possibly) demo.
 *
 * N.B. Node-specific configuration file names MUST be used in mock network. Nodes in "real" network may also use
 * default "node.properties" file name. Node-specific file names are composed according to the following rules
 * (see [ProvisionOfferingService.loadConfigProperties]):
 * * file extension is ".properties"
 * * the name contains sequence of components of X500Name (CN,OU,O,L,C) associated with the given node. Components
 * should be separated by "-" (dash) character;
 * * if some X500Name component is omitted (is null), an empty string is used;
 * * if some X500Name component contains non-alphanumeric characters, these characters should be replaced with "_"
 * (underscores)
 */
object X500Names {
    // config file name is "--ArkS-SPB-RU.properties"
    val Consumer = CordaX500Name("ArkS", "SPB", "RU")
    // config file name is "--Charlez_Ponzi-Boston-US.properties"
    val MoneyLender1 = CordaX500Name("Charlez Ponzi", "Boston", "US")
    // config file name is "--Bernard_Lawrence_Madoff-New_York-US.properties"
    val MoneyLender2 = CordaX500Name("Bernard Lawrence Madoff", "New York", "US")
    // config file name is "--Louis_Bleriot-Paris-FR.properties"
    val AirlineProvider = CordaX500Name("Louis Bleriot", "Paris", "FR")
    // config file name is "--John_Kemp_Starley-London-GB.properties"
    val BikeProvider = CordaX500Name("John Kemp Starley", "London", "GB")
    // config file name is "--Karl_Friedrich_Michael_Benz-Karlsruhe_Baden_Baden-DE.properties"
    val CarProvider = CordaX500Name("Karl Friedrich Michael Benz", "Karlsruhe/Baden-Baden", "DE")
    // config file name is "--Charon-Tartar-GR.properties"
    val FerryProvider = CordaX500Name("Charon", "Tartar", "GR")
    // config file name is "--Adam_Kozlevitch-Czestochowa-PL.properties"
    val TaxiProvider = CordaX500Name("Adam Kozlevitch", "Czestochowa", "PL")
    // config file name is "--George_Stephenson-Whelm-GB.properties"
    val TrainProvider = CordaX500Name("George Stephenson", "Whelm", "GB")
}