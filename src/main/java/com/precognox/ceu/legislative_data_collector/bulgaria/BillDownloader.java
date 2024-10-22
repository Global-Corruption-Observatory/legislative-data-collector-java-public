package com.precognox.ceu.legislative_data_collector.bulgaria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.bulgaria.json.BillListItem;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class BillDownloader {

    private final PageSourceRepository pageSourceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GET_BILL_API_ENDPOINT_TEMPLATE = "https://www.parliament.bg/api/v1/bill/{0}";

    public BillDownloader(PageSourceRepository pageSourceRepository) {
        this.pageSourceRepository = pageSourceRepository;
    }

    public void downloadBillPages() {
        List<PageSource> billListResponse = pageSourceRepository.findByCountryAndPageType(
                Country.BULGARIA, PageType.BILL_LIST_JSON.name()
        );

        billListResponse.stream()
                .map(PageSource::getRawSource)
                .map(this::parseJsonList)
                .flatMap(List::stream)
                .map(BillListItem::getActId)
                .map(billId -> MessageFormat.format(GET_BILL_API_ENDPOINT_TEMPLATE, billId))
                .map(this::processBillUrl)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .peek(entity -> log.info("Storing bill: {}", entity.getPageUrl()))
                .forEach(pageSourceRepository::save);
    }

    private List<BillListItem> parseJsonList(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<PageSource> processBillUrl(String apiUrl) {
        HttpResponse<String> resp = Unirest.get(apiUrl).asString();

        if (resp.isSuccess()) {
            PageSource entity = PageSource.builder()
                    .country(Country.BULGARIA)
                    .rawSource(resp.getBody())
                    .pageUrl(apiUrl)
                    .pageType(PageType.BILL_JSON.name())
                    .build();

            return Optional.of(entity);
        }

        return Optional.empty();
    }

}
