package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;

import java.util.HashMap;
import java.util.Map;

public class Constants {
    //long and short forms of month names
    static final Map<String, Integer> MONTH_TRANSLATIONS = new HashMap<>();
    static final Map<String, LegislativeDataRecord.BillStatus> BILL_STATUS_MAPPING = new HashMap<>();

    static {
        MONTH_TRANSLATIONS.put("janeiro", 1);
        MONTH_TRANSLATIONS.put("jan", 1);
        MONTH_TRANSLATIONS.put("fevereiro", 2);
        MONTH_TRANSLATIONS.put("fev", 2);
        MONTH_TRANSLATIONS.put("março", 3);
        MONTH_TRANSLATIONS.put("mar", 3);
        MONTH_TRANSLATIONS.put("abril", 4);
        MONTH_TRANSLATIONS.put("abr", 4);
        MONTH_TRANSLATIONS.put("maio", 5);
        MONTH_TRANSLATIONS.put("mai", 5);
        MONTH_TRANSLATIONS.put("junho", 6);
        MONTH_TRANSLATIONS.put("jun", 6);
        MONTH_TRANSLATIONS.put("julho", 7);
        MONTH_TRANSLATIONS.put("jul", 7);
        MONTH_TRANSLATIONS.put("agosto", 8);
        MONTH_TRANSLATIONS.put("ago", 8);
        MONTH_TRANSLATIONS.put("setembro", 9);
        MONTH_TRANSLATIONS.put("set", 9);
        MONTH_TRANSLATIONS.put("outubro", 10);
        MONTH_TRANSLATIONS.put("out", 10);
        MONTH_TRANSLATIONS.put("novembro", 11);
        MONTH_TRANSLATIONS.put("nov", 11);
        MONTH_TRANSLATIONS.put("dezembro", 12);
        MONTH_TRANSLATIONS.put("dez", 12);

        BILL_STATUS_MAPPING.put("arquivada", LegislativeDataRecord.BillStatus.REJECT);
        BILL_STATUS_MAPPING.put("rejeitada", LegislativeDataRecord.BillStatus.REJECT);
        BILL_STATUS_MAPPING.put("prejudicada", LegislativeDataRecord.BillStatus.REJECT);

        BILL_STATUS_MAPPING.put("apensado ao pl", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("devolvida ao autor", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("devolvida ao autora", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("devolvida à autor", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("devolvida à autora", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("aguardando", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("pronta para pauta", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("remetida", LegislativeDataRecord.BillStatus.ONGOING);
        BILL_STATUS_MAPPING.put("matéria com a relatoria", LegislativeDataRecord.BillStatus.ONGOING);

        BILL_STATUS_MAPPING.put("transformado em lei ordinária", LegislativeDataRecord.BillStatus.PASS);
        BILL_STATUS_MAPPING.put("transformada na lei ordinária", LegislativeDataRecord.BillStatus.PASS);
        BILL_STATUS_MAPPING.put("transformada em norma jurídica", LegislativeDataRecord.BillStatus.PASS);
    }

}
