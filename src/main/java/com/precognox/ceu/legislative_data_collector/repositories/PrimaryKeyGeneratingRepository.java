package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.australia.AuCountrySpecificVariables;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.function.Supplier;

@Slf4j
@Repository
public class PrimaryKeyGeneratingRepository {

    @Delegate
    private final LegislativeDataRepository legislativeDataRepository;

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public PrimaryKeyGeneratingRepository(
            LegislativeDataRepository legislativeDataRepository,
            EntityManager entityManager,
            PlatformTransactionManager platformTransactionManager) {
        this.entityManager = entityManager;
        this.legislativeDataRepository = legislativeDataRepository;

        transactionTemplate = new TransactionTemplate(platformTransactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public <T> T merge(T entity) {
        return entityManager.merge(entity);
    }

    public synchronized <S extends LegislativeDataRecord> S mergeInNewTransaction(S entity) {
        return transactionTemplate.execute(status -> merge(entity));
    }

    public synchronized <S extends LegislativeDataRecord> S mergeAndFlushInNewTransaction(S entity) {
        return transactionTemplate.execute(status -> {
            merge(entity);
            entityManager.flush();
            return null;
        });
    }

    public synchronized <S extends LegislativeDataRecord> S save(S entity) {
        try {
            if (entity.getRecordId() == null) {
                entity.setRecordId(getPrimaryKey(entity));
            }

            return transactionTemplate.execute(status -> {
                S saved = legislativeDataRepository.save(entity);
                log.info("Saved record: {}", saved.getRecordId());

                return saved;
            });
        } catch (Exception e) {
            log.error("Error when saving entity: " + entity, e);
        }

        return entity;
    }

    public synchronized <S extends LegislativeDataRecord> S updateIa(S entity) {
        try {
            return transactionTemplate.execute(status -> {
                S updated = merge(entity);
                entity.getImpactAssessments().forEach(entityManager::persist);
                log.info("Updated impact assessments for record: {}", updated.getRecordId());

                return updated;
            });
        } catch (Exception e) {
            log.error("Error when updating impact assessments for entity: " + entity, e);
        }
        return entity;
    }

    private void saveOrUpdate(ImpactAssessment ia, LegislativeDataRecord dataRecord) {
        if (ia.getDataRecord() == null) {
            ia.setDataRecord(dataRecord);
        }
        if (ia.getId() == null) {
            entityManager.persist(ia);
        } else {
            entityManager.merge(ia);
        }
    }

    private void saveOrUpdate(Amendment amendment, LegislativeDataRecord dataRecord) {
        if (amendment.getDataRecord() == null) {
            amendment.setDataRecord(dataRecord);
        }
        if (amendment.getId() == null) {
            entityManager.persist(amendment);
        } else {
            entityManager.merge(amendment);
        }
    }

    @Transactional
    public void saveOrUpdate(AuCountrySpecificVariables auCountrySpecificVariables, LegislativeDataRecord dataRecord) {
        if (auCountrySpecificVariables.getLegislativeDataRecord() == null) {
            auCountrySpecificVariables.setLegislativeDataRecord(dataRecord);
        }
        if (auCountrySpecificVariables.getId() == null) {
            entityManager.persist(auCountrySpecificVariables);
        } else {
            entityManager.merge(auCountrySpecificVariables);
        }
    }

    @Transactional
    public void saveOrUpdate(LegislativeDataRecord dataRecord) {
        if (dataRecord.getId() == null) {
            save(dataRecord);
        } else {
            entityManager.merge(dataRecord);
        }
    }

    public void deleteWithRelatedData(LegislativeDataRecord dataRecord) {
        dataRecord.getAmendments().forEach(amendment -> entityManager.remove(amendment));
        dataRecord.getImpactAssessments().forEach(ia -> entityManager.remove(ia));

        delete(dataRecord);
    }

    private <S extends LegislativeDataRecord> String getPrimaryKey(S entity) {
        return switch (entity.getCountry()) {
            case UK -> getId("UK", legislativeDataRepository::getNextUniqueIdForUk);
            case HUNGARY -> getId("HU", legislativeDataRepository::getNextUniqueIdForHungary);
            case COLOMBIA -> getId("CO", legislativeDataRepository::getNextUniqueIdForColombia);
            case CHILE -> getId("CH", legislativeDataRepository::getNextUniqueIdForChile);
            case BRAZIL -> getId("BR", legislativeDataRepository::getNextUniqueIdForBrazil);
            case JORDAN -> getId("JO", legislativeDataRepository::getNextUniqueIdForJordan);
            case RUSSIA -> getId("RU", legislativeDataRepository::getNextUniqueIdForRussia);
            case INDIA -> getId("IN", legislativeDataRepository::getNextUniqueIdForIndia);
            case BULGARIA -> getId("BG", legislativeDataRepository::getNextUniqueIdForBulgaria);
            case USA -> getId("USA", legislativeDataRepository::getNextUniqueIdForUsa);
            case GEORGIA -> getId("GE", legislativeDataRepository::getNextUniqueIdForGeorgia);
            case SWEDEN -> getId("SW", legislativeDataRepository::getNextUniqueIdForSweden);
            case AUSTRALIA -> getId("AU", legislativeDataRepository::getNextUniqueIdForAustralia);
            case POLAND -> getId("PL", legislativeDataRepository::getNextUniqueIdForPoland);
            case SOUTH_AFRICA -> getId("SA", legislativeDataRepository::getNextUniqueIdForSouthAfrica);
            default -> throw new IllegalStateException("Unexpected value: " + entity.getCountry());
        };
    }

    private String getId(String countryKey, Supplier<Integer> getIdFromDB) {
        Integer id = getIdFromDB.get();
        String paddedId = StringUtils.leftPad(Integer.toString(id), 5, '0');

        return countryKey + paddedId;
    }

    public LegislativeDataRepository getDelegate() {
        return legislativeDataRepository;
    }

}
