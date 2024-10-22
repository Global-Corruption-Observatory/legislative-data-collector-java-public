package com.precognox.ceu.legislative_data_collector.colombia.constants;

import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaOriginatorVariables;
import com.precognox.ceu.legislative_data_collector.entities.colombia.OriginTypeColombia;

import java.util.HashMap;
import java.util.Map;

public class ColombianTranslations {

    public static final Map<String, OriginTypeColombia> ORIGIN_TYPE_TRANSLATIONS = Map.of(
            "gubernamental", OriginTypeColombia.GOVERNMENT,
            "legislativa", OriginTypeColombia.PARLIAMENTARY,
            "mixta", OriginTypeColombia.MIX,
            "otras entidades", OriginTypeColombia.OTHER_ENTITIES,
            "popular", OriginTypeColombia.POPULAR
    );


    public static final String FILING = "Radicado";
    public static final String PUBLICATION = "Publicación";
    public static final String FIRST_DEBATE = "Aprobado Primer Debate";
    public static final String SECOND_DEBATE = "Aprobado Segundo Debate";
    public static final String THIRD_DEBATE = "Aprobado Tercer Debate";
    public static final String FOURTH_DEBATE = "Aprobado Cuarto Debate";
    public static final String FIFTH_DEBATE = "Aprobado Quinto Debate";
    public static final String SIXTH_DEBATE = "Aprobado Sexto Debate";
    public static final String SEVENTH_DEBATE = "Aprobado Séptimo Debate";
    public static final String EIGHTH_DEBATE = "Aprobado Octavo Debate";
    public static final String SANCTION_NORMAL = "Sancionado como Ley";
    public static final String SANCTION_LEGISLATIVE = "Acto Legislativo";
    public static final String FIRST_AND_THIRD_DEBATE = "Aprobado Primer y Tercer Debate";

    public static final Map<String, String> LEGISLATIVE_STAGE_TRANSLATIONS = new HashMap<>();
    public static final Map<String, Integer> MONTHS_TRANSLATIONS = new HashMap<>();
    public static final Map<String, ColombiaOriginatorVariables.Gender> GENDER_TRANSLATIONS = Map.of(
            "masculino", ColombiaOriginatorVariables.Gender.MALE,
            "femenino", ColombiaOriginatorVariables.Gender.FEMALE
    );
    public static final Map<String, String> COMMITTEE_ROLES = Map.of(
            "Primera", "Human Rights, Statutory laws, Territorial organization, Legislative Acts, Organization of the national administration.",
            "Segunda", "International Relations, Defense, Trade.",
            "Tercera", "Treasury, Credit, Finances, Banks, Taxes.",
            "Cuarta", "Procurement, Fiscal Control, Budget.",
            "Quinta", "Environment, Mining, Agriculture.",
            "Sexta", "Communications, Science and Technology, Infrastructure, Transportation, tourism, education and culture.",
            "Séptima", "Health, Housing, Administrative Career, Social Security, Family, Sports."
    );
    public static final Map<String, String> STAGES_TO_SOURCE_TYPES = Map.of(
            PUBLICATION, TextType.BILL_TEXT.label,
            SANCTION_LEGISLATIVE, PageType.LAW_TEXT.label,
            SANCTION_NORMAL,PageType.LAW_TEXT.label,
            FIRST_DEBATE, TextType.AMENDMENT_STAGE_1_TEXT.label,
            SECOND_DEBATE, TextType.AMENDMENT_STAGE_2_TEXT.label,
            THIRD_DEBATE, TextType.AMENDMENT_STAGE_3_TEXT.label,
            FOURTH_DEBATE, TextType.AMENDMENT_STAGE_4_TEXT.label,
            FIRST_AND_THIRD_DEBATE, TextType.AMENDMENT_STAGE_13_JOINED_TEXT.label
    );

    static {
        LEGISLATIVE_STAGE_TRANSLATIONS.put(FILING, "Filing");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(PUBLICATION, "Publication");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(FIRST_DEBATE, "First debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(SECOND_DEBATE, "Second debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(THIRD_DEBATE, "Third debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(FOURTH_DEBATE, "Fourth debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(FIFTH_DEBATE, "Fifth debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(SIXTH_DEBATE, "Sixth debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(SEVENTH_DEBATE, "Seventh debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(EIGHTH_DEBATE, "Eight debate");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(SANCTION_NORMAL, "Presidential sanction");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(SANCTION_LEGISLATIVE, "Presidential sanction");
        LEGISLATIVE_STAGE_TRANSLATIONS.put(FIRST_AND_THIRD_DEBATE, "First and third debate");

    }

    static {
        MONTHS_TRANSLATIONS.put("enero", 1);
        MONTHS_TRANSLATIONS.put("febrero", 2);
        MONTHS_TRANSLATIONS.put("marzo", 3);
        MONTHS_TRANSLATIONS.put("abril", 4);
        MONTHS_TRANSLATIONS.put("mayo", 5);
        MONTHS_TRANSLATIONS.put("junio", 6);
        MONTHS_TRANSLATIONS.put("julio", 7);
        MONTHS_TRANSLATIONS.put("agosto", 8);
        MONTHS_TRANSLATIONS.put("septiembre", 9);
        MONTHS_TRANSLATIONS.put("octubre", 10);
        MONTHS_TRANSLATIONS.put("noviembre", 11);
        MONTHS_TRANSLATIONS.put("diciembre", 12);
    }
}
