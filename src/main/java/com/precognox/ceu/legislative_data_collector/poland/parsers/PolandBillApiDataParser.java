package com.precognox.ceu.legislative_data_collector.poland.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.constants.PolishTranslations;
import com.precognox.ceu.legislative_data_collector.poland.json.BillJson;
import com.precognox.ceu.legislative_data_collector.poland.json.BillJsonReference;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus.PASS;

@Slf4j
@Service
public class PolandBillApiDataParser {

    private static final String BILL_API_URL_TEMPLATE = "https://api.sejm.gov.pl/eli/acts/%s";
    private static final String PDF_URL_TEMPLATE = "https://isap.sejm.gov.pl/isap.nsf/download.xsp/";
    private static final String LAW_PAGE_URL_TEMPLATE = "https://isap.sejm.gov.pl/isap.nsf/DocDetails.xsp?id=";
    private static final String BILL_TEXT_URL_TEMPLATE_TERM3 = "https://orka.sejm.gov.pl/Rejestrd.nsf/wgdruku/%s/$file/%s.pdf";
    private static final String BILL_TEXT_URL_TEMPLATE_FROM_TERM4 = "https://orka.sejm.gov.pl/Druki%ska.nsf/wgdruku/%s";
    private static final String BILL_TEXT_URL_TEMPLATE_FROM_TERM7 = "https://www.sejm.gov.pl/Sejm%s.nsf/druk.xsp?nr=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PdfParser pdfParser;
    private final PageSourceRepository pageSourceRepository;


    @Autowired
    public PolandBillApiDataParser(PdfParser pdfParser, PageSourceRepository pageSourceRepository
    ) {
        this.pdfParser = pdfParser;
        this.pageSourceRepository = pageSourceRepository;
    }

    public BillJson parseBillApiData(PageSource source, LegislativeDataRecord dataRecord) {
        log.info("Start processing bill-API response for {}", dataRecord.getRecordId());

        BillJson billJson = new BillJson();
        try {
            billJson = objectMapper.readValue(source.getRawSource(), BillJson.class);
            dataRecord.setCountry(Country.POLAND);
            dataRecord.setLawId(billJson.getLawId());
            dataRecord.setBillTitle(billJson.getBillTitle());
            setOriginalLawFromTitle(billJson, dataRecord);
            dataRecord.setBillPageUrl(LAW_PAGE_URL_TEMPLATE + billJson.getBillAddressForPdfText());
            dataRecord.setAltBillPageUrl(source.getPageUrl());
            // the entry point in the parsing is the LAW ITSELF, so the related bill has always the status: PASS
            // (fixed in bugfix - round 1)
            dataRecord.setBillStatus(PASS);
            dataRecord.setDatePassing(DateUtils.parsePolandDate(billJson.getDatePassing()));
            dataRecord.setDateEnteringIntoForce(DateUtils.parsePolandDate(billJson.getDateEnteringIntoForce()));
            getLawTextUrlFromJson(billJson).ifPresent(dataRecord::setLawTextUrl);
            dataRecord.setLawSize(parseLawTextSize(dataRecord));

            // Bill ID's structure was requested by the annotator
            if (!billJson.getPrints().isEmpty()) {
                String processNumber = billJson.getPrints().get(0).getProcessNumber();
                String romanTermNo =
                        PolishTranslations.getRomanTermNoFromTermNo(billJson.getPrints().get(0).getTerm().toString());
                String billId = "Druk Sejmowy nr " + processNumber + " - " + romanTermNo + " kadencja";
                dataRecord.setBillId(billId); // like: "Druk Sejmowy nr 15 - III kadencja"
                getBillTextUrlFromBillId(processNumber, romanTermNo).ifPresent(dataRecord::setBillTextUrl);
                dataRecord.setBillSize(parseBillTextSize(dataRecord));
            }
            if (billJson.getBillReference() != null) {
                setModifiedLawsCountAndIds(billJson, dataRecord);
                setAffectingLawsCountAndFirstDate(billJson, dataRecord);
            }
            log.info("Finished processing bill-API response for {}", dataRecord.getRecordId());
        } catch (JsonProcessingException e) {
            log.error("Bill JSON processing error at {} " + e, dataRecord.getRecordId());
        }

        return billJson;
    }

    private Optional<String> getBillTextUrlFromBillId(String processNumber, String romanTermNo) {
        List<String> term4to6 = List.of("IV", "V", "VI");
        List<String> term7to9 = List.of("VII", "VIII", "IX");
        String billTextUrl = null;
        if (romanTermNo.equalsIgnoreCase("III")) {
            billTextUrl =
                    String.format(BILL_TEXT_URL_TEMPLATE_TERM3, processNumber, processNumber);
        } else if (term4to6.contains(romanTermNo)) {
            billTextUrl =
                    String.format(BILL_TEXT_URL_TEMPLATE_FROM_TERM4, getLatinTermNo(romanTermNo), processNumber);
        } else if (term7to9.contains(romanTermNo)) {
            billTextUrl =
                    String.format(BILL_TEXT_URL_TEMPLATE_FROM_TERM7, getLatinTermNo(romanTermNo), processNumber);
        }
        return !StringUtils.isBlank(billTextUrl) ? Optional.of(billTextUrl) : Optional.empty();
    }

    private String getLatinTermNo(String romanTermNo) {
        String result;
        switch (romanTermNo) {
            case "III" -> result = "3";
            case "IV" -> result = "4";
            case "V" -> result = "5";
            case "VI" -> result = "6";
            case "VII" -> result = "7";
            case "VIII" -> result = "8";
            case "IX" -> result = "9";
            default -> result = "";
        }
        return result;
    }

    private Optional<String> parseLawText(String lawTextUrl) {
        return pdfParser.tryPdfTextExtraction(lawTextUrl);
    }

    private Integer parseLawTextSize(LegislativeDataRecord dataRecord) {
        if (dataRecord.getLawText() != null &&
                (!dataRecord.getLawText().equals(PdfParser.SCANNED_LABEL) && !dataRecord.getLawText().equals(PdfParser.ERROR_LABEL))) {
            return TextUtils.getLengthWithoutWhitespace(dataRecord.getLawText());
        } else {
            return null;
        }
    }

    private Integer parseBillTextSize(LegislativeDataRecord dataRecord) {
        if (dataRecord.getBillText() != null && !dataRecord.getBillText().isEmpty() &&
                (!dataRecord.getBillText().equals(PdfParser.SCANNED_LABEL) && !dataRecord.getBillText().equals(PdfParser.ERROR_LABEL))) {
            return TextUtils.getLengthWithoutWhitespace(dataRecord.getBillText());
        } else {
            return null;
        }
    }

    // sample: https://isap.sejm.gov.pl/isap.nsf/download.xsp/WDU19360370282/O/D19360282.pdf
    // If ../U/....pdf does exist (that's an updated version of text), we need that url, if not, we need .../O/...pdf one.
    private Optional<String> getLawTextUrlFromJson(BillJson billJson) {
        String pdfName = "";
        String docTypeUrlParam = "";

        for (BillJson.BillTextJson text : billJson.getBillTexts()) {
            if (text.getBillTextFileType().equalsIgnoreCase("U")) {
                pdfName = text.getBillTextFileName();
                docTypeUrlParam = "/U/";
            } else if ((!text.getBillTextFileType().equalsIgnoreCase("U")) && (text.getBillTextFileType().equalsIgnoreCase("O"))) {
                pdfName = text.getBillTextFileName();
                docTypeUrlParam = "/O/";
            }
        }
        return Optional.of(PDF_URL_TEMPLATE + billJson.getBillAddressForPdfText() + docTypeUrlParam + pdfName);
    }

    private void setOriginalLawFromTitle(BillJson billJson, LegislativeDataRecord dataRecord) {
        if (billJson.getBillTitle().contains("o zmianie")) {
            dataRecord.setOriginalLaw(Boolean.FALSE);
        } else {
            dataRecord.setOriginalLaw(Boolean.TRUE);
        }
    }

    private void setModifiedLawsCountAndIds(BillJson billJson, LegislativeDataRecord dataRecord) {
        List<BillJsonReference.AmendedAct> amendedActs = billJson.getBillReference().getAmendedActs();
        if (amendedActs == null || amendedActs.isEmpty()) {
            dataRecord.setModifiedLawsCount(0);
        } else {
            dataRecord.setModifiedLawsCount(amendedActs.size());
            List<String> actIds = amendedActs.stream()
                    .map(BillJsonReference.AmendedAct::getAmendedActId)
                    .toList();
            Set<String> modifiedLawIds = new HashSet<>();
            for (String actId : actIds) {
                Optional<PageSource> optSource = pageSourceRepository.findByPageUrl(String.format(BILL_API_URL_TEMPLATE, actId));
                optSource.ifPresent(source -> {
                    try {
                        BillJson bj = objectMapper.readValue(source.getRawSource(), BillJson.class);
                        modifiedLawIds.add(bj.getLawId());
                    } catch (JsonProcessingException e) {
                        log.error("JSON-processing error at modified law ids on record {}", dataRecord.getRecordId());
                    }
                });
            }
            dataRecord.setModifiedLaws(modifiedLawIds);
        }
    }

    private void setAffectingLawsCountAndFirstDate(BillJson billJson, LegislativeDataRecord dataRecord) {
        List<BillJsonReference.ImplementingAct> implementingActs = billJson.getBillReference().getImplementingActs();
        if (implementingActs == null || implementingActs.isEmpty()) {
            dataRecord.setAffectingLawsCount(0);
        } else if (implementingActs.size() == 1) {
            dataRecord.setAffectingLawsCount(implementingActs.size());
            dataRecord.setAffectingLawsFirstDate(implementingActs.get(0).getImplementingActDate() != null ?
                    DateUtils.parsePolandDate(implementingActs.get(0).getImplementingActDate()) : null);
        } else {
            List<BillJsonReference.ImplementingAct> sortedImplementingActsByDate = implementingActs.stream()
                    .sorted(new ImplementingActDateComparator())
                    .toList();
            dataRecord.setAffectingLawsCount(sortedImplementingActsByDate.size());
            dataRecord.setAffectingLawsFirstDate(DateUtils.parsePolandDate(
                    sortedImplementingActsByDate.get(0).getImplementingActDate()));
        }
    }

    // sometimes the date key-value pair is missing completely from JSON
    private class ImplementingActDateComparator implements Comparator<BillJsonReference.ImplementingAct> {

        @Override
        public int compare(final BillJsonReference.ImplementingAct imp1, final BillJsonReference.ImplementingAct imp2) {
            if (imp1.getImplementingActDate() == null && imp2.getImplementingActDate() == null) {
                return 0;
            } else if (imp1.getImplementingActDate() == null) {
                return 1;
            } else if (imp2.getImplementingActDate() == null) {
                return -1;
            } else {
                return imp1.getImplementingActDate().compareTo(imp2.getImplementingActDate());
            }
        }
    }
}
