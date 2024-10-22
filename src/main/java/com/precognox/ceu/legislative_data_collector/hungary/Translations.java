package com.precognox.ceu.legislative_data_collector.hungary;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores English names for the Hungarian expressions.
 */
public class Translations {
    public static final Map<String, String> LAW_TYPE_TRANSLATIONS = Map.of(
            "törvényjavaslat", "Simple bill",
            "alaptörvény elfogadására, illetve módosítására irányuló javaslat", "Proposal for Constitution amendment",
            "költségvetési törvényjavaslat", "Budget bill",
            "törvényjavaslat nemzetközi szerződésről", "Bill on international treaty",
            "törv.j. nemzetk. szerződésről", "Bill on international treaty",
            "zárszámadás", "Other"
    );

    public static final Map<String, String> PROCEDURE_TYPE_TRANSLATIONS = Map.of(
            "kivételes tárgyalásban", "exceptional negotiation",
            "normál", "regular",
            "kivételes sürgős", "exceptional, urgent",
            "sürgős és kivételes", "exceptional, urgent",
            "sürgős tárgyalásban", "urgent negotiation",
            "határozati házszabályi rendelkezésektől való eltéréssel", "process deviating from house rules"
    );

    public static final Map<String, String> LEGISLATIVE_STAGES_TRANSLATIONS = Map.of(
            "általános vita megkezdve", "Opening of the general debate",
            "bizottság kijelölve részletes vita lefolytatására", "Appointing the committee to conduct the detailed debate",
            "Az illetékes bizottság kijelölve", "Appointing the committee to conduct the detailed debate",
            "bizottsági jelentés(ek) vitája megkezdve", "Debate on the committee report(s)",
            "bizottsági jelentések és az összegző módosító javaslat vitája megkezdve", "Debate on the committee reports and on the summary proposal for an amendment",
            "Köztársasági elnök aláírta", "Signing by the president",
            "részletesvita-szakasz megnyitva", "Opening of the detailed debate",
            "részletes vita megkezdve", "Opening of the detailed debate"
    );

    public static final Map<String, String> COMMITTEE_ROLE_TRANSLATIONS = new HashMap<>();

    static {
        COMMITTEE_ROLE_TRANSLATIONS.put("A Házszabály 107.§ (1) alapján", "Appointed committee based on 107§ (1) of the Rules of Procedure");
        COMMITTEE_ROLE_TRANSLATIONS.put("A Házszabály 98.§ (4) alapján", "Appointed committee based on 98§ (4) of the Rules of Procedure");
        COMMITTEE_ROLE_TRANSLATIONS.put("Első helyen kijelölt bizottságként", "First appointed as a committee");
        COMMITTEE_ROLE_TRANSLATIONS.put("HHSZ. 79. §-a (1) bekezdésének b) pontja alapján", "Appointed committee Based on 79§ (1)(b) of the Rules of Procedure");
        COMMITTEE_ROLE_TRANSLATIONS.put("HHSZ. 98. §-ának (4) bekezdése alapján", "Appointed committee Based on 98§ (4) of the Rules of Procedure");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság", "Appointed committee");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság (AB határozatának megfelelő módosító javaslat benyújtására)", "Appointed committee (to propose amendments in accordance with the decision of the Constitutional Court)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság (házszabályi rendelkezésektől való eltérés alapján)", "Appointed committee (based on the derogation from the provisions of the Rules of Procedure)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság (kiegészítő részletes vita lefolytatására)", "Appointed committee (for supplementary detailed debate)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság (köztársasági elnök átiratának tárgyalására)", "Appointed committee (to discuss the transcript of the President)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság (részletes vita lefolytatása kivételes eljárás során)", "Appointed committee (to conduct a detailed discussion in an exceptional procedure)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság (tárgysorozatba vételre)", "Appointed committee (for inclusion in the agenda)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság (zárószavazást előkészítő módosító javaslat tárgyalására)", "Appointed committee (to discuss a proposal for an amendment in preparation for the final vote)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság 79.§ (1) a)", "Appointed committee Based on 79§ (1)(a) of the Rules of Procedure");
        COMMITTEE_ROLE_TRANSLATIONS.put("Kijelölt bizottság 79.§ (1) b)", "Appointed committee Based on 79§ (1)(b) of the Rules of Procedure");
        COMMITTEE_ROLE_TRANSLATIONS.put("Nyilatkozata alapján kijelölt bizottság (részletes vita lefolytatására)", "Committee appointed on the basis of its declaration (to conduct a detailed debate)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Törvényalkotási bizottság eljárása", "Procedure of the legislative committee");
        COMMITTEE_ROLE_TRANSLATIONS.put("Törvényalkotási bizottság eljárása (döntés tárgysorozatba-vételi kérelemről)", "Procedure of the legislative committee (decision on request for inclusion in the agenda)");
        COMMITTEE_ROLE_TRANSLATIONS.put("Törvényalkotási bizottság szerepkörében történő eljárás", "Procedure of the legislative committee");
        COMMITTEE_ROLE_TRANSLATIONS.put("Vitához kapcsolódó bizottság", "Committee related to the debate");
    }

}
