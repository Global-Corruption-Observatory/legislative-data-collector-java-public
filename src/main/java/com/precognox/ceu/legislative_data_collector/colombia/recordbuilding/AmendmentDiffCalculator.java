package com.precognox.ceu.legislative_data_collector.colombia.recordbuilding;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.utils.TextDiffTool;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.FIRST_DEBATE;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.FOURTH_DEBATE;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.LEGISLATIVE_STAGE_TRANSLATIONS;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.THIRD_DEBATE;

/**
 * A class to coordinate the calculation of the amendment_sizes
 */
@Slf4j
public class AmendmentDiffCalculator {
    private final TextDiffTool diffTool;
    private final LegislativeDataRecord record;

    public AmendmentDiffCalculator(LegislativeDataRecord record) {
        this.diffTool = new TextDiffTool();
        this.record = record;
    }

    public int getCharDiffForAmendment(Amendment amendment) throws DataCollectionException {
        if (!record.getAmendments().contains(amendment)) {
            throw new DataCollectionException(String.format("Amendment does not belong to the record! Amendment id: %s record id: %s", amendment.getId(), record.getRecordId()));
        }
        //Amendment texts starting with 'AMENDMENT text' are placeholders for when the url is known, and not the real texts, so they are handled as if they were missing
        Optional<String> amendedText = Optional.ofNullable(amendment.getAmendmentText()).filter(text -> !text.startsWith("AMENDMENT text"));
        Optional<String> previousText;
        if (amendment.getStageName().equals(LEGISLATIVE_STAGE_TRANSLATIONS.get(FIRST_DEBATE)) ||
                (record.getProcedureTypeStandard().equals(LegislativeDataRecord.ProcedureType.EXCEPTIONAL) && amendment.getStageName().equals(LEGISLATIVE_STAGE_TRANSLATIONS.get(THIRD_DEBATE)))
        ) {
            //If 1st debate or the 3rd debate of and exceptional procedure ( joined 1st and 3rd ) bill it needs to be compared with bill text
            previousText = Optional.ofNullable(record.getBillText());
        } else if( record.getProcedureTypeStandard().equals(LegislativeDataRecord.ProcedureType.EXCEPTIONAL) && amendment.getStageName().equals(LEGISLATIVE_STAGE_TRANSLATIONS.get(FOURTH_DEBATE)) ) {
            //If 1st and 3rd debate is joined, the 4th debate needs to be compared with the 2nd one
            previousText = getAmendmentText(amendment.getStageNumber() - 2);
        } else {
            previousText = getAmendmentText(amendment.getStageNumber() - 1);
        }
        if (amendedText.isPresent() && previousText.isPresent()) {
            return (int) diffTool.getCharDifference(previousText.get(), amendedText.get());
        } else {
            throw new DataCollectionException(String.format("Text is blank for amendment calculation for %s", amendment.getStageName()));
        }
    }

    private Optional<String> getAmendmentText(int stageNumber) {
        return record.getAmendments()
                .stream()
                .filter(amendment -> amendment.getStageNumber() == stageNumber)
                .findFirst()
                .map(Amendment::getAmendmentText)
                .filter(text -> !text.startsWith("AMENDMENT text"));
    }
}