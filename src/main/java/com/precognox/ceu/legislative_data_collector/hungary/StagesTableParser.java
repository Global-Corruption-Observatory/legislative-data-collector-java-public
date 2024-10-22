package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StagesTableParser {

    private final LegislativeDataRecord record;
    private final Element stagesTable;

    public static final Map<Integer, String> LEGISLATIVE_STAGES = Map.of(
            1, "bizottság kijelölve részletes vita lefolytatására|Az illetékes bizottság kijelölve",
            2, "általános vita megkezdve",
            3, "részletesvita-szakasz megnyitva|részletes vita megkezdve",
            4, "bizottsági jelentések és az összegző módosító javaslat vitája megkezdve|bizottsági jelentés\\(ek\\) vitája megkezdve",
            5, "Köztársasági elnök aláírta"
    );

    private static final int PASSING_STAGE_NUM = 5;
    private static final int COMMITTEE_STAGE_NUM = 1;

    public StagesTableParser(LegislativeDataRecord record, Element stagesTable) {
        this.record = record;
        this.stagesTable = stagesTable;
    }

    public void parse() {
        List<LegislativeStage> stages = stagesTable.getElementsByTag("tr")
                .stream()
                .skip(2)
                .filter(this::isStageRow)
                .map(stageRow -> {
                    Elements cells = stageRow.getElementsByTag("td");
                    String stageDate = cells.get(0).text().trim();
                    String stageName = cells.get(1).text().trim();

                    return buildStageEntity(stageDate, stageName);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LegislativeStage::getStageNumber))
                .toList();

        record.setStages(stages);
        record.setStagesCount(record.getStages().size());

        parseDatePassing();
        parseDateCommittee();
        record.setCommitteeHearingCount(parseCommitteeHearings());
    }

    private boolean isStageRow(Element row) {
        Elements cells = row.getElementsByTag("td");

        if (cells.size() > 1) {
            return isStageName(cells.get(1).text().trim());
        }

        return false;
    }

    private boolean isStageName(String stageLabel) {
        return LEGISLATIVE_STAGES.values()
                .stream()
                .anyMatch(stageLabel::matches);
    }

    @Nullable
    private LegislativeStage buildStageEntity(String stageDate, String stageName) {
        Optional<Map.Entry<Integer, String>> matchedStageEntry = LEGISLATIVE_STAGES.entrySet()
                .stream()
                .filter(stageEntry -> stageName.matches(stageEntry.getValue()))
                .findFirst();

        return matchedStageEntry.map(entry -> {
            String translatedStageName = Translations.LEGISLATIVE_STAGES_TRANSLATIONS.get(stageName);

            return new LegislativeStage(
                    entry.getKey(),
                    DateUtils.parseHungaryDate(stageDate),
                    translatedStageName != null ? translatedStageName : stageName
            );
        }).orElse(null);
    }

    private void parseDatePassing() {
        record.getStages().stream()
                .filter(stg -> stg.getStageNumber().equals(PASSING_STAGE_NUM))
                .findFirst()
                .ifPresent(stg -> record.setDatePassing(stg.getDate()));
    }

    private void parseDateCommittee() {
        record.getStages().stream()
                .filter(stg -> stg.getStageNumber().equals(COMMITTEE_STAGE_NUM))
                .findFirst()
                .ifPresent(stg -> record.setCommitteeDate(stg.getDate()));
    }

    private int parseCommitteeHearings() {
        long hearingsCount = stagesTable.getElementsByTag("tr")
                .stream()
                .skip(2)
                .map(tr -> tr.getElementsByTag("td"))
                .filter(cells -> cells.size() > 1)
                .map(cells -> cells.get(1).text().trim())
                .filter("bizottság bejelentette részletes vita lefolytatását"::equals)
                .count();

        return Math.toIntExact(hearingsCount);
    }
}
