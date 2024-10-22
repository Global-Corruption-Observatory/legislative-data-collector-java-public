package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.queue.ExecutorServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Slf4j
@Service
public class IaTextParser {

    private final PrimaryKeyGeneratingRepository recordRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    private static final Pattern IA_TITLE_REGEX = Pattern.compile(
            "^\\d+(\\.\\d+)? Konsekvenser\\n|^\\d+(\\.\\d+)? Konsekvenser av förslagen\\n|^\\d+(\\.\\d+)? Konsekvensanalys\\n|^\\d+(\\.\\d+)? Konsekvensutredningar\\n",
            Pattern.MULTILINE
    );

    @Autowired
    public IaTextParser(PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository) {
        this.recordRepository = primaryKeyGeneratingRepository;
    }

    @Transactional
    public void processAllRecords() {
        log.info("Processing IA text for all records...");

        recordRepository.streamAllWithBillText().forEach(
                record -> executor.submit(() -> processRecord(record))
        );

        ExecutorServiceUtils.waitForCompletion(executor);
    }

    private void processRecord(LegislativeDataRecord record) {
        record.setImpactAssessmentDone(hasIa(record.getBillText()));
        recordRepository.mergeInNewTransaction(record);
        log.info("Updated record {} with ia_dummy: {}", record.getRecordId(), record.getImpactAssessmentDone());
    }

    public Boolean hasIa(String billText) {
        String clean = TextUtils.trimLines(billText);

        return IA_TITLE_REGEX.matcher(clean).find();
    }

}
