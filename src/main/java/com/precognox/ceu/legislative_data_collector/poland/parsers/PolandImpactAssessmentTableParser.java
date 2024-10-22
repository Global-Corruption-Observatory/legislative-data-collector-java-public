package com.precognox.ceu.legislative_data_collector.poland.parsers;

import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.IaTextCollector;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for parsing impact assessments in one legislative process. From term3 and term6 url is like:
 * https://orka.sejm.gov.pl/rexdomk6.nsf/Opdodr?OpenPage&nr=3610 (term = 6 & process-number = 3610), from term7 to term9
 * url is like: https://www.sejm.gov.pl/Sejm8.nsf/opinieBAS.xsp?nr=2423.
 */
@Slf4j
@Service
public class PolandImpactAssessmentTableParser {

    private final IaTextCollector iaTextCollector;

    @Autowired
    public PolandImpactAssessmentTableParser(IaTextCollector iaTextCollector) {
        this.iaTextCollector = iaTextCollector;
    }

    public void parseImpactAssessment(PageSource source, LegislativeDataRecord dataRecord) {
        log.info("Processing impact assessment table for {} started", dataRecord.getLawId());

        List<ImpactAssessment> iaList = new ArrayList<>();
        Document parsedPage = Jsoup.parse(source.getRawSource());

        boolean pageHasOlderDesign = source.getPageUrl().startsWith("https://orka.sejm");

        Element iaTableBody = pageHasOlderDesign ? getIaTableBodyFromTerm3(parsedPage)
                : getIaTableBodyFromTerm7(parsedPage);

        if (iaTableBody == null || iaTableBody.getElementsByTag("tr").isEmpty()) {
            dataRecord.setImpactAssessmentDone(Boolean.FALSE);
            log.info("Impact assessment table is missing or empty for: {}", source.getPageUrl());
        } else {
            iaTableBody.getElementsByTag("tr").forEach(row -> {
                ImpactAssessment ia = new ImpactAssessment();
                List<Element> cellData = row.getElementsByTag("td");
                ia.setDate(LocalDate.parse(cellData.get(0).text(),
                        DateTimeFormatter.ofPattern(pageHasOlderDesign ? "dd-MM-yyyy" : "yyyy-MM-dd")));
                ia.setOriginalUrl(cellData.get(1).getElementsByTag("a").attr("href").trim());
                ia.setDataRecord(dataRecord);
                iaTextCollector.processImpactAssessmentText(ia);
                ia.setTitle(cellData.get(2).text());
                iaList.add(ia);
            });
            dataRecord.setImpactAssessmentDone(Boolean.TRUE);
            dataRecord.setImpactAssessments(iaList);
        }
        log.info("Processing impact assessment table for {} finished", dataRecord.getLawId());
    }

    @Nullable
    private Element getIaTableBodyFromTerm3(Document parsedPage) {
        return parsedPage.body().getElementsByTag("tr").get(4)
                .getElementsByTag("td").first().getElementsByTag("table").first()
                .getElementsByTag("tbody").first();
    }

    @Nullable
    private Element getIaTableBodyFromTerm7(Document parsedPage) {
        return parsedPage.body().getElementById("view:_id1:_id2:facetMain").child(0)
                .getElementsByTag("tbody").first();
    }
}
