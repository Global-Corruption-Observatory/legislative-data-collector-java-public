package com.precognox.ceu.legislative_data_collector.poland.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.json.ProcessJson;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PolandProcessApiDataParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PageSourceRepository pageSourceRepository;

    @Autowired
    public PolandProcessApiDataParser(PageSourceRepository pageSourceRepository) {
        this.pageSourceRepository = pageSourceRepository;
    }

    public void parseProcessApiData(String url, LegislativeDataRecord dataRecord) {
        log.info("Processing stored process-API response for {} started", dataRecord);

        PageSource source = pageSourceRepository.findByPageUrl(url).get();
        try {
            ProcessJson processJson = objectMapper.readValue(source.getRawSource(), ProcessJson.class);
            dataRecord.setDateIntroduction(DateUtils.parsePolandDate(processJson.getIntroductionDate()));
            setOriginType(dataRecord, processJson);
            log.info("Finished processing stored process-API response for {}", dataRecord);
        } catch (JsonProcessingException e) {
            log.error("Process JSON processing error at {} " + e, dataRecord.getRecordId());
        }
    }

    private static void setOriginType(LegislativeDataRecord dataRecord, ProcessJson processJson) {
        switch (processJson.getProcessTitle().substring(0, 7)) {
            case "Rządowy" -> dataRecord.setOriginType(OriginType.GOVERNMENT);
            case "Poselsk" -> dataRecord.setOriginType(OriginType.PARLIAMENT);
            case "Obywate" -> dataRecord.setOriginType(OriginType.CIVIC);
            case "Senacki" -> dataRecord.setOriginType(OriginType.SENATE);
            case "Przedst" -> dataRecord.setOriginType(OriginType.PRESIDENT);
            case "Komisyj" -> dataRecord.setOriginType(OriginType.COMMITTEE);
            default -> dataRecord.setOriginType(OriginType.OTHER);
        }
    }
}
