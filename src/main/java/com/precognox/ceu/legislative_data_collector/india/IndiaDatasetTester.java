package com.precognox.ceu.legislative_data_collector.india;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
@Service
public class IndiaDatasetTester {

    private final EntityManager entityManager;
    private final LegislativeDataRepository billRepository;

    public IndiaDatasetTester(EntityManager entityManager, LegislativeDataRepository billRepository) {
        this.entityManager = entityManager;
        this.billRepository = billRepository;
    }

    public void runTests() {
        runConsistencyChecks();
        runRegressionTests();
    }

    private void runConsistencyChecks() {
        log.info("Running consistency checks for India...");

        //result should be 0 for all of these
        String test1 = "SELECT COUNT(*) " +
                "FROM {h-schema}bill_main_table " +
                "WHERE original_law = false " +
                "AND (affecting_laws_count > 0 " +
                "OR affecting_laws_first_date IS NOT NULL);";

        String test2 = "SELECT COUNT(*) FROM {h-schema}bill_main_table " +
                "WHERE original_law = true " +
                "AND bill_main_table.modified_laws_count > 0;";

        String test3 = "SELECT COUNT(*) FROM {h-schema}bill_main_table " +
                "WHERE bill_status = 'REJECT' " +
                "AND (modified_laws_count > 0 or affecting_laws_count > 0);";

        String test4 = "SELECT COUNT(*) FROM {h-schema}bill_main_table " +
                "WHERE bill_status = 'PASS' " +
                "AND original_law = false " +
                "AND bill_main_table.modified_laws_count <> 1;";

        Map<String, String> tests = Map.of(
                "Amending laws being modified by other laws", test1,
                "Original laws modifying others", test2,
                "Rejected bills with modified or affecting laws", test3,
                "Passed bills modifying more than one other law", test4
        );

        tests.forEach((testName, testQuery) -> {
            BigInteger result =
                    (BigInteger) entityManager.createNativeQuery(testQuery).getSingleResult();

            if (result.equals(BigInteger.ZERO)) {
                log.info("%s - OK".formatted(testName));
            } else {
                log.error("Wrong result (%d) for query: %s".formatted(result, testName));
            }
        });

        log.info("Finished consistency checks");
    }

    private void runRegressionTests() {
        log.info("Running regression tests...");

        testBill("3 of 1958", bill -> {
            assertEquals(0, bill.getModifiedLawsCount());
            assertEquals(14, bill.getAffectingLawsCount());
            assertEquals(LocalDate.of(1966, 8, 31), bill.getAffectingLawsFirstDate());
        });

        testBill("135 of 1999", bill -> {
            assertEquals(0, bill.getModifiedLawsCount());
            assertEquals(1, bill.getAffectingLawsCount());
            assertEquals(LocalDate.of(2009, 2, 5), bill.getAffectingLawsFirstDate());
        });

        testBill("96 of 2006", bill -> {
            assertEquals(1, bill.getModifiedLawsCount());
            assertEquals(0, bill.getAffectingLawsCount());
            assertNull(bill.getAffectingLawsFirstDate());
        });

        testBill("116 of 2005", bill -> {
            assertEquals(0, bill.getModifiedLawsCount());
            assertNull(bill.getAffectingLawsCount());
            assertNull(bill.getAffectingLawsFirstDate());
        });

        testBill("82 of 1987", bill -> {
            assertEquals(0, bill.getModifiedLawsCount());
            assertEquals(2, bill.getAffectingLawsCount());
            assertEquals(LocalDate.of(1994, 10, 29), bill.getAffectingLawsFirstDate());
        });

        log.info("Finished regression tests");
    }

    private void testBill(String billId, Consumer<LegislativeDataRecord> testFunction) {
        billRepository.findByCountryAndBillId(Country.INDIA, billId).ifPresentOrElse(bill -> {
            try {
                testFunction.accept(bill);
            } catch (AssertionError e) {
                log.error("Assertion failed for bill {}", bill.getBillId(), e);
            }
        }, () -> {
            throw new AssertionError("Bill doesn't exist: " + billId);
        });
    }
}
