package com.precognox.ceu.legislative_data_collector.common;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

/**
 * Overwrites the record IDs of stored records to remove gaps in the numbering.
 */
@Slf4j
@Service
public class SequentialIdMapper {

    @Autowired
    private LegislativeDataRepository legislativeDataRepository;

    @Autowired
    private PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository;

    @Transactional
    public void reassignIds(Country country) {
        log.info("Remapping record IDs...");

        int startId = 1;
        int maxId = 99_999;
        int currentId = maxId;

        //set db sequence?
        //sort by dates intro
        List<LegislativeDataRecord> page = legislativeDataRepository.findAllSortedByDateIntro(country);

        for (LegislativeDataRecord record : page) {
            record.setRecordId(country.getPrefix() + StringUtils.leftPad(Integer.toString(currentId), 5, '0'));
            primaryKeyGeneratingRepository.mergeInNewTransaction(record);
            log.info("Set ID: {}", currentId);
            currentId--;
        }

        currentId = startId;

        for (LegislativeDataRecord record : page) {
            record.setRecordId(country.getPrefix() + StringUtils.leftPad(Integer.toString(currentId), 5, '0'));
            primaryKeyGeneratingRepository.mergeInNewTransaction(record);
            log.info("Set ID: {}", currentId);
            currentId++;
        }

        log.info("Finished setting record IDs");
    }

}
