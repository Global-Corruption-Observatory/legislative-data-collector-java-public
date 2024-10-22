package com.precognox.ceu.legislative_data_collector.poland;

import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
import com.precognox.ceu.legislative_data_collector.utils.DocUtils;
import com.precognox.ceu.legislative_data_collector.utils.FileDownloader;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

/**
 * This class is responsible for collecting impact assessment texts. Three different file extensions are possible, ".rtf",
 * ".doc" or ".docx".
 */
@Slf4j
@Service
public class IaTextCollector {

    private static final String BASE_URL = "https://orka.sejm.gov.pl";

    private final FileDownloader fileDownloader;

    @Autowired
    public IaTextCollector(FileDownloader fileDownloader) {
        this.fileDownloader = fileDownloader;
    }

    public void processImpactAssessmentText(ImpactAssessment ia) {
        DocUtils docUt = new DocUtils(fileDownloader);
        String fileUrl = ia.getOriginalUrl().startsWith("/") ? BASE_URL + ia.getOriginalUrl() : ia.getOriginalUrl();
        if (fileUrl.endsWith(".rtf") || fileUrl.endsWith(".doc") || fileUrl.endsWith(".docx")) {
            try {
                Optional<String> docText = docUt.downloadDocText(fileUrl);
                docText.ifPresent(text -> {
                    ia.setText(text);
                    ia.setSize(TextUtils.getLengthWithoutWhitespace(text));
                });
            } catch (IOException | SAXException | TikaException e) {
                log.error("Failed to extract doc text from impact assessment " + ia);
            }
        }
    }
}
