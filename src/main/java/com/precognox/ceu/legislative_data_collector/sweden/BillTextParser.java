package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses variables from the bill text (date introduction, date entering into force, original law).
 */
@Slf4j
@Service
public class BillTextParser {

    private final PrimaryKeyGeneratingRepository recordRepository;

    public static final Pattern DATE_INTRO_REGEX = Pattern.compile(
            "(?:Stockholm|Harpsund) den (\\d{1,2} \\w+ \\d{4})",
            Pattern.MULTILINE
    );

    private static final Pattern DATE_ENTERING_FORCE_REGEX = Pattern.compile(
            "föreslås träda i kraft den (\\d{1,2}\\s+\\w+\\s+\\d{4})"
    );

    private final Pattern MODIFYING_LAW_LABEL =
            Pattern.compile("(Lagändringarna|Lagändringen) föreslås träda i kraft den");

    private static final String ORIGINAL_LAW_LABEL = "Den nya lagen föreslås träda i kraft den"; //can also be: Den nya lagen och lagändringarna föreslås träda i kraft

    @Autowired
    public BillTextParser(PrimaryKeyGeneratingRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void processAllRecords() {
        log.info("Processing bill text for all records...");

        recordRepository.streamAllForBillTextParser().forEach(this::processRecord);
    }

    private void processRecord(LegislativeDataRecord record) {
        parseVariables(record);
        recordRepository.mergeInNewTransaction(record);

        log.info("Processed record: {}", record.getRecordId());
    }

    public void parseVariables(LegislativeDataRecord record) {
        parseDateIntro(record);
        parseDateEnteringForce(record);
        parseOriginalLaw(record);
    }

    private void parseDateIntro(LegislativeDataRecord record) {
        parseDateFromBillText(record, DATE_INTRO_REGEX).ifPresent(record::setDateIntroduction);
    }

    private void parseDateEnteringForce(LegislativeDataRecord record) {
        parseDateFromBillText(record, DATE_ENTERING_FORCE_REGEX).ifPresent(record::setDateEnteringIntoForce);
    }

    private Optional<LocalDate> parseDateFromBillText(LegislativeDataRecord record, Pattern pattern) {
        if (record.getBillText() != null) {
            return pattern.matcher(record.getBillText())
                    .results()
                    .findFirst()
                    .map(matchResult -> matchResult.group(1))
                    .map(Utils::parseDateExpr);
        }

        return Optional.empty();
    }

    private void parseOriginalLaw(LegislativeDataRecord record) {
        if (record.getBillText() != null && OriginType.GOVERNMENT.equals(record.getOriginType())) {
            if (record.getBillText().contains(ORIGINAL_LAW_LABEL)) {
                record.setOriginalLaw(Boolean.TRUE);
            } else if (MODIFYING_LAW_LABEL.matcher(record.getBillText()).results().findAny().isPresent()) {
                record.setOriginalLaw(Boolean.FALSE);
            }
        }
    }

}
