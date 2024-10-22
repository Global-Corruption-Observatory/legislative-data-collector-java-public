package com.precognox.ceu.legislative_data_collector.australia;

import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class AustraliaCommonFunctions {

    private PageSourceRepository pageSourceRepository;
    private final TransactionTemplate transactionTemplate;

    public Optional<String> getLawId(String url) {
        String regex = "https://www\\.legislation\\.gov\\.au/([A-Za-z0-9]+)/.*";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);

        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
