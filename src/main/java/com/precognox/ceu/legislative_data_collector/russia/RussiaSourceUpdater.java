package com.precognox.ceu.legislative_data_collector.russia;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RussiaSourceUpdater {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public RussiaSourceUpdater(
            EntityManager entityManager, TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    @SneakyThrows
    @Transactional
    public void updateSources() {
        //only re-download existing pages in DB + save collection date
        ExecutorService pool = Executors.newFixedThreadPool(6);

        String query = "SELECT ps.pageUrl FROM PageSource ps" +
                " WHERE ps.country = :country" +
                " AND ps.pageType = :pageType" +
                " AND ps.collectionDate IS NULL";

        List<String> urls = entityManager.createQuery(query, String.class)
                .setParameter("country", Country.RUSSIA)
                .setParameter("pageType", "BILL")
                .getResultList();

        log.info("Found {} pages to update", urls.size());
        urls.forEach(p -> pool.submit(() -> process(p)));

        pool.shutdown();
        boolean done = pool.awaitTermination(3, TimeUnit.DAYS);

        if (done) {
            log.info("Finished processing");
        } else {
            log.error("Pool terminated");
        }
    }

    private void process(String url) {
        Unirest.get(url).asString().ifSuccess(resp -> {
            String qlString = "UPDATE PageSource ps" +
                    " SET ps.rawSource = :source, ps.size = :size, ps.collectionDate = :date" +
                    " WHERE ps.pageUrl = :url AND ps.pageType = :type";

            try {
                transactionTemplate.execute(status -> {
                    Query query = entityManager.createQuery(qlString)
                            .setParameter("source", resp.getBody())
                            .setParameter("size", resp.getBody().length())
                            .setParameter("date", LocalDate.now())
                            .setParameter("url", url)
                            .setParameter("type", "BILL");

                    int updated = query.executeUpdate();

                    if (updated == 1) {
                        log.info("Updated page: {} with size: {}", url, resp.getBody().length());
                    } else {
                        status.setRollbackOnly();
                    }

                    return status;
                });
            } catch (Exception e) {
                log.error("", e);
            }
        }).ifFailure(resp -> log.error("Error response: {} for page: {}", resp.getStatus(), url));
    }

}
