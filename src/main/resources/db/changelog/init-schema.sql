CREATE SEQUENCE hibernate_sequence;

CREATE TABLE BILL_MAIN_TABLE
(
    "id"                              SERIAL PRIMARY KEY,
    "record_id"                       VARCHAR UNIQUE NOT NULL,
    "country"                         VARCHAR,
    "bill_id"                         VARCHAR,
    "law_id"                          VARCHAR,
    "bill_nature"                     VARCHAR,
    "bill_page_url"                   VARCHAR,
    "bill_type"                       VARCHAR,
    "type_of_law_eng"                 VARCHAR,
    "bill_title"                      VARCHAR,
    "origin_type"                     VARCHAR,
    "stages_count"                    INT,
    "bill_status"                     VARCHAR,
    "date_introduction"               DATE,
    "committee_date"                  DATE,
    "date_passing"                    DATE,
    "date_entering_into_force"        DATE,
    "committee_count"                 INT,
    "committee_hearing_count"         INT,
    "committee_depth"                 INT,
    "ia_dummy"                        BOOL,
    "amendment_count"                 INT,
    "bill_text"                       TEXT,
    "bill_size"                       INT,
    "bill_text_url"                   VARCHAR,
    "law_text"                        TEXT,
    "law_text_url"                    VARCHAR,
    "law_size"                        INT,
    "modified_laws_count"             INT,
    "affecting_laws_count"            INT,
    "affecting_laws_first_date"       DATE,
    "procedure_type_standard"         VARCHAR,
    "procedure_type_eng"              VARCHAR,
    "procedure_type_national"         VARCHAR,
    "plenary_size"                    INT,
    "final_vote_for"                  INT,
    "final_vote_against"              INT,
    "final_vote_abst"                 INT,
    "raw_source_url"                  VARCHAR,
    "raw_source"                      TEXT,
    "has_royal_assent"                BOOL,
    "has_programme_motion"            BOOL,
    "has_money_resolution"            BOOL,
    "has_ways_and_means_resolution"   BOOL,
    "bill_text_general_justification" VARCHAR,
    "category"                        VARCHAR,
    "leg_status"                      VARCHAR,
    "related_total"                   INT,
    "related_regulation"              INT,
    "related_directions"              INT,
    "related_decisions"               INT,
    "related_explanation"             INT
);

CREATE TABLE ORIGINATORS
(
    "record_id"              INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "originator_name"        VARCHAR,
    "originator_affiliation" VARCHAR
);

CREATE TABLE LEGISLATIVE_STAGES
(
    "record_id"    INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "stage_number" INT,
    "date"         DATE,
    "name"         VARCHAR,
    "debate_size"  INT
);

CREATE TABLE AFFECTED_LAWS
(
    "record_id"       INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "modified_law_id" VARCHAR
);

CREATE TABLE COMMITTEES
(
    "record_id"      INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "committee_name" VARCHAR,
    "committee_role" VARCHAR
);

CREATE TABLE IMPACT_ASSESSMENTS
(
    "id"           SERIAL PRIMARY KEY,
    "record_id"    INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "ia_title"     VARCHAR,
    "ia_date"      DATE,
    "original_url" VARCHAR,
    "ia_text"      TEXT,
    "ia_size"      INT
);

CREATE TABLE AMENDMENTS
(
    "id"                       SERIAL PRIMARY KEY,
    "record_id"                INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "amendment_id"             VARCHAR,
    "date"                     DATE,
    "amendment_plenary"        VARCHAR,
    "amendment_stage_number"   INT,
    "amendment_stage_name"     VARCHAR,
    "amendment_committee_name" VARCHAR,
    "amendment_outcome"        VARCHAR,
    "amendment_vote_for"       INT,
    "amendment_vote_against"   INT,
    "amendment_vote_abst"      INT,
    "amendment_text_url"       VARCHAR,
    "amendment_text"           TEXT
);

CREATE TABLE AMENDMENT_ORIGINATORS
(
    "amendment_id"           INT NOT NULL REFERENCES AMENDMENTS,
    "originator_name"        VARCHAR,
    "originator_affiliation" VARCHAR
);

CREATE TABLE BILL_VERSIONS
(
    "record_id"       INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "house"           VARCHAR,
    "text"            VARCHAR,
    "text_size_chars" INT,
    "text_source_url" VARCHAR
);

CREATE TABLE ERRORS
(
    "record_id" INT NOT NULL REFERENCES BILL_MAIN_TABLE,
    "error"     VARCHAR
);

create sequence uk_generic_id_seq;
create sequence hu_generic_id_seq;
create sequence br_generic_id_seq;
create sequence jo_generic_id_seq;
