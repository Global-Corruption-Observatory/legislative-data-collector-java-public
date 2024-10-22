package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.hungary.PageSourceParser.LAW_TYPE_CONSTITUTION_CHANGE;

/**
 * Parses the modified laws from the bill titles or law texts.
 */
@Slf4j
@Service
public class ModifiedLawParser {

    private final PrimaryKeyGeneratingRepository recordRepository;

    private static final String LAW_ID_REGEX = "(\\d\\d\\d\\d\\.\\s?(évi)?\\s[MDCLXVI]+\\.\\s?törvény)";
    private static final Pattern BILL_TITLE_REFERENCED_LAW_REGEX = Pattern.compile(LAW_ID_REGEX);
    private static final Pattern REFERENCED_LAW_REGEX =
            Pattern.compile("(\\d{4})\\.\\s?(?:évi)?\\s?([IVXLCM]+)\\.\\s?törvény");

    private static final List<Pattern> BILL_TEXT_REFERENCED_LAW_REGEXES = List.of(
            Pattern.compile(LAW_ID_REGEX + "\\s*módosítása", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "\\s*módosításáról", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,75}ponttal\\segészül\\ski", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,75}bekezdéssel\\segészül\\ski", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,40}az\\salábbiak\\sszerint\\smódosul", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,40}bekezdése\\shelyébe", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,40}§-a\\shelyébe", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,75}helyébe\\sa\\skövetkező\\srendelkezés\\slép", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,75}pontokkal\\segészül\\ski", Pattern.MULTILINE),
            Pattern.compile(LAW_ID_REGEX + "[\\s\\S]{0,75}a\\skövetkező\\sszöveggel\\slép\\shatályba", Pattern.MULTILINE),
            Pattern.compile("Hatályát veszti[\\s\\S]{0,75}" + LAW_ID_REGEX, Pattern.MULTILINE)
    );

    @Autowired
    public ModifiedLawParser(PrimaryKeyGeneratingRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void processAllRecords() {
        recordRepository.streamAll(Country.HUNGARY)
                .peek(this::parseModifiedLaws)
                .peek(record -> log.info("Found {} modified laws for record {}", record.getModifiedLawsCount(),  record.getRecordId()))
                .forEach(recordRepository::mergeInNewTransaction);
    }

    public void parseModifiedLaws(LegislativeDataRecord record) {
        if (LAW_TYPE_CONSTITUTION_CHANGE.equals(record.getBillType())) {
            record.setModifiedLawsCount(1);
            record.setModifiedLaws(Set.of("Constitution"));
        } else if (record.getOriginalLaw() == Boolean.FALSE) {
            //check title first for referenced laws
            Set<String> results = getAllMatches(BILL_TITLE_REFERENCED_LAW_REGEX.matcher(record.getBillTitle()));

            if (results.isEmpty()) {
                results = parseFromText(record);
            }

            if (!results.isEmpty()) {
                record.setModifiedLaws(results);
                record.setModifiedLawsCount(results.size());
            }
        }
    }

    public Set<String> parseFromText(LegislativeDataRecord record) {
        //check law and bill text
        String textToCheck = ObjectUtils.firstNonNull(record.getLawText(), record.getBillText(), "");
        String clean = cleanText(textToCheck);

        return BILL_TEXT_REFERENCED_LAW_REGEXES.stream()
                .map(p -> p.matcher(clean))
                .flatMap(Matcher::results)
                .map(r -> r.group(1))
                .map(match -> match.replaceAll("\n", " "))
                .map(this::normalizeReferencedLaw)
                .collect(Collectors.toSet());
    }

    private String normalizeReferencedLaw(String origText) {
        Matcher matcher = REFERENCED_LAW_REGEX.matcher(origText);

        if (matcher.find()) {
            return matcher.group(1) + "/" + matcher.group(2);
        }

        return origText;
    }

    private Set<String> getAllMatches(Matcher matcher) {
        return matcher.results().map(MatchResult::group).map(this::normalizeReferencedLaw).collect(Collectors.toSet());
    }

    private String cleanText(String text) {
        //replace multiple spaces with one
        return text.replaceAll(" {2,}", " ").replace(" .", ".");
    }

}
