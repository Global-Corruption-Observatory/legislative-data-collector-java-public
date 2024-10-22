package com.precognox.ceu.legislative_data_collector.usa.tests

import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository
import lombok.extern.slf4j.Slf4j
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Checks for previous known errors (regressions) in the dataset.
 */
@Slf4j
@Service
class UsaDatasetTester(private val repository: LegislativeDataRepository) {

    fun runTests() {
        println("Running tests for USA...")
        runRegressionTests()
        testLogicalErrors()

        println("Tests passed")
    }

    private fun testLogicalErrors() {

    }

    private fun runRegressionTests() {

    }

    private fun checkDatePassing(billId: String, datePassing: LocalDate) {
        assertTrue(repository.existsByBillIdAndDatePassing(billId, datePassing))
    }

}
