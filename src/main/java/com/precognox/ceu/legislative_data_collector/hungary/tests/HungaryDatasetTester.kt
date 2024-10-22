package com.precognox.ceu.legislative_data_collector.hungary.tests

import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository
import lombok.extern.slf4j.Slf4j
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Checks for previous known errors (regressions) in the dataset.
 */
@Slf4j
@Service
class HungaryDatasetTester(private val repository: LegislativeDataRepository) {

    fun runTests() {
        println("Running tests for Hungary...")
        runRegressionTests()
        testLogicalErrors()

        println("Tests passed")
    }

    private fun testLogicalErrors() {
        //date passing not empty and bill status != passed
        //amendment count != number of amendments
    }

    private fun runRegressionTests() {
        checkDatePassing("2002/T/65", LocalDate.of(2002, 7, 4))
        checkDatePassing("2008/T/5827", LocalDate.of(2008, 6, 26))
        checkDatePassing("2020/T/9475", LocalDate.of(2020, 3, 30))
        checkDatePassing("2009/T/9981", LocalDate.of(2009, 7, 2))
        checkDatePassing("2020/T/10309", LocalDate.of(2020, 5, 27))
        checkDatePassing("2020/T/10310", LocalDate.of(2020, 6, 8))
        checkDatePassing("2020/T/10311", LocalDate.of(2020, 6, 8))
        checkDatePassing("2009/T/11083", LocalDate.of(2009, 12, 21))

        //individual bills
        var bill3031 = repository.findByBillId("2007/T/3031").get()
        assertEquals(bill3031.stagesCount, 4)
        assertEquals(bill3031.finalVoteFor, 343)
        assertEquals(bill3031.finalVoteAgainst, 0)
        assertEquals(bill3031.finalVoteAbst, 0)

        var bill5827 = repository.findByBillId("2008/T/5827").get()
        assertEquals(bill5827.committeeCount, 1)
        assertEquals(bill5827.finalVoteFor, 352)
        assertEquals(bill5827.finalVoteAgainst, 0)
        assertEquals(bill5827.finalVoteAbst, 0)

        var bill11083 = repository.findByBillId("2009/T/11083").get()
        assertEquals(bill11083.committeeCount, 5)
        assertEquals(bill11083.finalVoteFor, 175)
        assertEquals(bill11083.finalVoteAgainst, 163)
        assertEquals(bill11083.finalVoteAbst, 4)
        assertEquals(bill11083.stagesCount, 4)

        var bill9981 = repository.findByBillId("2009/T/9981").get()
        assertEquals(bill9981.committeeCount, 1)
        assertEquals(bill9981.finalVoteFor, 347)
        assertEquals(bill9981.finalVoteAgainst, 14)
        assertEquals(bill9981.finalVoteAbst, 0)

        var bill17427 = repository.findByBillId("T/17427").get()
        assertEquals(bill17427.committeeHearingCount, 1)
        assertEquals(bill17427.originators.size, 1)
        //assertEquals(bill17427.originators[0].name, "kormány (agrárminiszter)")
    }

    private fun checkDatePassing(billId: String, datePassing: LocalDate) {
        assertTrue(repository.existsByBillIdAndDatePassing(billId, datePassing))
    }

}
