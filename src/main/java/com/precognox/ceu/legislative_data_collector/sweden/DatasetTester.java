package com.precognox.ceu.legislative_data_collector.sweden;

import com.google.common.base.Function;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DatasetTester {

    private final LegislativeDataRepository recordRepository;

    @Autowired
    public DatasetTester(LegislativeDataRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public void runSwedenChecks() {
        log.info("Running checks on dataset...");

        for (TestParams test : getTests()) {
            recordRepository.findByBillPageUrl(test.billPageUrl).ifPresentOrElse(r -> {
                Object value = test.getter.apply(r);

                if (!test.expectedValue.equals(value)) {
                    log.error(
                            "Wrong value for record {}: {}, expected value: {}",
                            r.getRecordId(),
                            value,
                            test.expectedValue
                    );
                }
            }, () -> log.error("Bill is missing: {}", test.billPageUrl));
        }

        log.info("Checks finished");
    }

    private List<TestParams> getTests() {
        List<TestParams> tests = new ArrayList<>();

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/budgetpropositionen-for-2023_ha031/",
                LegislativeDataRecord::getDateIntroduction,
                LocalDate.of(2022, 11, 3))
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/kvalificerad-yrkesutbildning-som_gt0374/",
                LegislativeDataRecord::getDateIntroduction,
                LocalDate.of(2006, 3, 16))
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/forstarkt-tilltradesforbud-vid-idrottsarrangemang_h10368/",
                LegislativeDataRecord::getOriginalLaw,
                false)
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/sanktionsavgift-for-overtradelse-av-bestammelserna_h103234/",
                LegislativeDataRecord::getOriginalLaw,
                false)
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/ett-likviditetsverktyg-for-fonder_ha0365/",
                LegislativeDataRecord::getOriginalLaw,
                false)
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/varandringsbudget-for-2023_ha0399/",
                LegislativeDataRecord::getOriginalLaw,
                false)
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/egs-andra-forenklingsdirektiv-och-den-svenska_gj0358/",
                LegislativeDataRecord::getDateIntroduction,
                LocalDate.of(1995, 10, 19))
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/budgetpropositionen-for-2019_h6031/",
                LegislativeDataRecord::getDateIntroduction,
                LocalDate.of(2018, 11, 9))
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/dubbelbeskattningsavtal-mellan-sverige-och_gm0382/",
                LegislativeDataRecord::getDateIntroduction,
                LocalDate.of(1999, 3, 8))
        );

        tests.add(
                new TestParams(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/egs-andra-forenklingsdirektiv-och-den-svenska_gj0358/",
                LegislativeDataRecord::getDateIntroduction,
                LocalDate.of(1995, 10, 19))
        );

        return tests;
    }

    @Data
    @AllArgsConstructor
    static class TestParams {
        private String billPageUrl;
        private Function<LegislativeDataRecord, Object> getter;
        private Object expectedValue;
    }

}
