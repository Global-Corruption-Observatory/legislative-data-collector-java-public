package com.precognox.ceu.legislative_data_collector.chile.recordbuilders;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.chile.ChileCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.chile.ChileDataParser.RECOLLECT_EVERYTHING;

@Slf4j
public abstract class RecordBuilder {

    public final static int LAW_BUILDER_CODE = 1;
    public final static int BILL_BUILDER_CODE = 2;

    protected ReadDatabaseService readService;
    @Getter
    protected LegislativeDataRecord record;

    public static RecordBuilder getRecordBuilder(int type) throws DataCollectionException {
        return switch (type) {
            case LAW_BUILDER_CODE -> new LawRecordBuilder();
            case BILL_BUILDER_CODE -> new BillRecordBuilder();
            default -> throw new DataCollectionException(String.format("Unknown record builder type %s", type));
        };
    }

    public void setReadService(ReadDatabaseService readService) {
        this.readService = readService;
    }

    public void tryBuildingRecord(PageSource page) throws DataCollectionException {
        try {
            Optional<LegislativeDataRecord> dbRecord = getRecordForSource(page);

            if (dbRecord.isEmpty() || RECOLLECT_EVERYTHING) {
                buildRecord(page, dbRecord);
            } else {
                throw new DataCollectionException("Record already exist for this source");
            }
        } catch (DataCollectionException ex) {
            throw new DataCollectionException(String.format("[Page id: %s] Collection error while building record: %s", page.getId(), ex.getMessage()));
        }
    }

    public void setUpCollections() {
        if (Objects.isNull(record.getErrors())) {
            record.setErrors(new HashSet<>());
        }
        if (Objects.isNull(record.getModifiedLaws())) {
            record.setModifiedLaws(new HashSet<>());
        }
        if (Objects.isNull(record.getAffectingLawsDetailed())) {
            record.setAffectingLawsDetailed(new ArrayList<>());
        }
        if (Objects.isNull(record.getAmendments())) {
            record.setAmendments(new ArrayList<>());
        }
        if (Objects.isNull(record.getBillVersions())) {
            record.setBillVersions(new ArrayList<>());
        }
        if (Objects.isNull(record.getCommittees())) {
            record.setCommittees(new ArrayList<>());
        }
        if (Objects.isNull(record.getImpactAssessments())) {
            record.setImpactAssessments(new ArrayList<>());
        }
        if (Objects.isNull(record.getStages())) {
            record.setStages(new ArrayList<>());
        }
        if (Objects.isNull(record.getOriginators())) {
            record.setOriginators(new ArrayList<>());
        }
    }

    protected ChileCountrySpecificVariables getCountryVariables() {
        if (Objects.isNull(record.getChileCountrySpecificVariables())) {
            record.setChileCountrySpecificVariables(new ChileCountrySpecificVariables());
            record.getChileCountrySpecificVariables().setLegislativeDataRecord(record);
        }

        return record.getChileCountrySpecificVariables();
    }

    protected abstract void buildRecord(PageSource source, Optional<LegislativeDataRecord> record) throws DataCollectionException;

    /** Returns the record for this source from the database or if there is none an empty Optional*/
    protected abstract Optional<LegislativeDataRecord> getRecordForSource(PageSource source);
}