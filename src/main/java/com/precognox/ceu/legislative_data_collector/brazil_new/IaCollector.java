package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Collects impact assessments from PDFs linked on the STARTING_PAGE, then tries to match them with existing records based on the law ID (the law ID is extracted from the IA itself). If a match is found, the IA is saved to the record.
 */
@Slf4j
@Service
public class IaCollector {

    private final PdfParser pdfParser;
    private final PrimaryKeyGeneratingRepository recordRepository;

    private static final String STARTING_PAGE = "https://www2.camara.leg.br/a-camara/estruturaadm/gestao-na-camara-dos-deputados/gestao-estrategica-na-camara-dos-deputados/arquivos-de-projetos-corporativos/ail";

    public IaCollector(PdfParser pdfParser, PrimaryKeyGeneratingRepository recordRepository) {
        this.pdfParser = pdfParser;
        this.recordRepository = recordRepository;
    }

    public void collectAll() {
        log.info("Starting IA data collection");

        try {
            Document page = Jsoup.connect(STARTING_PAGE).get();
            Elements pdfLinks = page.body().selectXpath("//div[@id='content']//a");

            pdfLinks.stream()
                    .map(link -> link.attr("href"))
                    .peek(link -> log.info("Processing IA text: {}", link))
                    .map(pdfParser::tryPdfTextExtraction)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(this::processPdf);
        } catch (IOException e) {
            log.error("Error when fetching page: {}", STARTING_PAGE, e);
        }
    }

    private void processPdf(String pdfText) {
        //extract law IDs like 'PROJETO DE LEI Nº 2.481, DE 2011' from first page odf the pdf
        Pattern lawReference = Pattern.compile("PROJETO DE LEI Nº ([\\d.]+), DE (\\d{4})");
        Optional<MatchResult> firstMatch = lawReference.matcher(pdfText).results().findFirst();

        if (firstMatch.isPresent()) {
            //find matching law
            String lawId = firstMatch.get().group(1) + "/" + firstMatch.get().group(2);

            recordRepository.findByLawId(lawId).ifPresent(record -> {
                log.info("Matching law found for ID: {}", lawId);

                ImpactAssessment ia = new ImpactAssessment();
                ia.setDataRecord(record);
                ia.setText(pdfText);
                ia.setSize(TextUtils.getLengthWithoutWhitespace(pdfText));

                record.setImpactAssessmentDone(true);
                record.setImpactAssessments(List.of(ia));
                recordRepository.mergeInNewTransaction(record);
            });
        }
    }

}
