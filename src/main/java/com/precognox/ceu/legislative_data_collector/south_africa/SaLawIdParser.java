package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class SaLawIdParser {
    private PageSourceRepository pageSourceRepository;

    public void parseLawId() {
        Pattern LAW_ID_PATTERN = Pattern.compile("\\d{1,3} of \\d{4}");
        List<PageSource> pageSources = pageSourceRepository.getPageSourcesByPageTypeAndCountry(
                SaPageType.ACT.name(), Country.SOUTH_AFRICA);

        pageSources.forEach(pageSource -> {
            Element page = Jsoup.parse(pageSource.getRawSource()).body();

            String modifiedLawId = page.getElementsByTag("h1").text();


            String lawId = LAW_ID_PATTERN
                    .matcher(modifiedLawId)
                    .results()
                    .map(MatchResult::group)
                    .findFirst().orElse(null);

            pageSourceRepository.updateMetadata(pageSource.getPageUrl(), "Act " + lawId);
            log.info("Updated page_source metadata with: {}", lawId);
        });

    }
}
