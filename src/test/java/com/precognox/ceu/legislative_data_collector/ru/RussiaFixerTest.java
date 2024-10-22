package com.precognox.ceu.legislative_data_collector.ru;

import com.precognox.ceu.legislative_data_collector.entities.*;
import com.precognox.ceu.legislative_data_collector.russia.RussiaFixer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.LocalDate;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getResourceAsString;
import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus.PASS;
import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus.REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class RussiaFixerTest {

    private final RussiaFixer instance = new RussiaFixer(null, null, null, null, null);

    static Stream<Arguments> testParamProvider() {
        try {
            LegislativeDataRecord expected_259246_6 = new LegislativeDataRecord(Country.RUSSIA);
            expected_259246_6.setBillId("259246-6");
            expected_259246_6.setBillPageUrl("https://sozd.duma.gov.ru/bill/259246-6");

            expected_259246_6.setBillStatus(PASS);
            expected_259246_6.setDatePassing(LocalDate.of(2013, 7, 23));
            expected_259246_6.setOriginalLaw(false);
            expected_259246_6.setOriginType(OriginType.GOVERNMENT);
            expected_259246_6.setBillTitle("О внесении изменений в Федеральный закон \"О негосударственных пенсионных фондах\" и отдельные законодательные акты Российской Федерации");

            return Stream.of(
                    arguments("259246-6", expected_259246_6)
            );
        } catch (Exception e) {
            System.err.println(e.toString());
            Assertions.fail(e);

            throw e;
        }
    }

    @Disabled
    @ParameterizedTest
    @MethodSource("testParamProvider")
    void testBills(String billId, LegislativeDataRecord expectedRecord) throws IOException {
        PageSource mockPage = new PageSource();
        mockPage.setPageUrl(expectedRecord.getBillPageUrl());
        mockPage.setPageType("BILL");
        mockPage.setCountry(Country.RUSSIA);
        mockPage.setRawSource(getResourceAsString("/russia/test_pages/%s_page_source.html".formatted(billId)));

        LegislativeDataRecord inputRecord = new LegislativeDataRecord(Country.RUSSIA);
        inputRecord.setBillId(billId);
        inputRecord.setBillPageUrl(expectedRecord.getBillPageUrl());

        instance.processPageSource(inputRecord, mockPage);

        assertEquals(expectedRecord, inputRecord);
    }

    @NotNull
    private LegislativeDataRecord setupAndGetResult(String billId) throws IOException {
        String testBillUrl = "https://sozd.duma.gov.ru/bill/" + billId;

        PageSource mockPage = new PageSource();
        mockPage.setPageUrl(testBillUrl);
        mockPage.setPageType("BILL");
        mockPage.setCountry(Country.RUSSIA);
        mockPage.setRawSource(getResourceAsString("/russia/test_pages/" + billId + "_page_source.html"));

        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBillId(billId);
        testRecord.setBillPageUrl(testBillUrl);

        instance.processPageSource(testRecord, mockPage);

        return testRecord;
    }

    @Test
    void test_259246_6() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("259246-6");

        assertEquals(PASS, testRecord.getBillStatus());
        assertEquals(LocalDate.of(2013, 7, 23), testRecord.getDatePassing());
        assertEquals(false, testRecord.getOriginalLaw());
        assertEquals(OriginType.GOVERNMENT, testRecord.getOriginType());
        assertEquals("О внесении изменений в Федеральный закон \"О негосударственных пенсионных фондах\" и отдельные законодательные акты Российской Федерации", testRecord.getBillTitle());

        assertEquals(8, testRecord.getStagesCount());
        assertEquals(8, testRecord.getStages().size());

        LegislativeStage first = testRecord.getStages().get(0);
        assertEquals(1, first.getStageNumber());
        assertEquals(LocalDate.of(2013, 4, 15), first.getDate());
        assertEquals("Внесение законопроекта в Государственную Думу", first.getName());

        LegislativeStage second = testRecord.getStages().get(1);
        assertEquals(2, second.getStageNumber());
        assertEquals(LocalDate.of(2013, 4, 18), second.getDate());
        assertEquals("Предварительное рассмотрение законопроекта, внесенного в Государственную Думу", second.getName());

        LegislativeStage third = testRecord.getStages().get(2);
        assertEquals(3, third.getStageNumber());
        assertEquals(LocalDate.of(2013, 5, 21), third.getDate());
        assertEquals("Рассмотрение законопроекта в первом чтении", third.getName());

        LegislativeStage fourth = testRecord.getStages().get(3);
        assertEquals(4, fourth.getStageNumber());
        assertEquals(LocalDate.of(2013, 6, 27), fourth.getDate());
        assertEquals("Рассмотрение законопроекта во втором чтении", fourth.getName());

        LegislativeStage fifth = testRecord.getStages().get(4);
        assertEquals(5, fifth.getStageNumber());
        assertEquals(LocalDate.of(2013, 7, 3), fifth.getDate());
        assertEquals("Рассмотрение законопроекта в третьем чтении", fifth.getName());

        LegislativeStage sixth = testRecord.getStages().get(5);
        assertEquals(6, sixth.getStageNumber());
        assertEquals(LocalDate.of(2013, 7, 4), sixth.getDate());
        assertEquals("Прохождение закона в Совете Федерации", sixth.getName());

        LegislativeStage stage7 = testRecord.getStages().get(6);
        assertEquals(7, stage7.getStageNumber());
        assertEquals(LocalDate.of(2013, 7, 23), stage7.getDate());
        assertEquals("Прохождение закона у Президента Российской Федерации", stage7.getName());

        LegislativeStage stage8 = testRecord.getStages().get(7);
        assertEquals(8, stage8.getStageNumber());
        assertEquals(LocalDate.of(2013, 7, 23), stage8.getDate());
        assertEquals("Опубликование закона", stage8.getName());
    }

    @Test
    void test_95041740_1() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("95041740-1");

        assertEquals(REJECT, testRecord.getBillStatus());
        assertNull(testRecord.getDatePassing());
        assertEquals(false, testRecord.getOriginalLaw());
        assertEquals(OriginType.GOVERNMENT, testRecord.getOriginType());
        assertEquals("О внесении изменений и дополнений в Закон Российской Федерации \"О статусе военнослужащих\", связанных с вопросами упорядочения предоставления отдельных льгот", testRecord.getBillTitle());

        assertEquals(2, testRecord.getCommitteeCount());

        assertEquals("Комитет Государственной Думы по труду и социальной политике", testRecord.getCommittees().get(0).getName());
        assertEquals("RESPONSIBLE COMMITTEE", testRecord.getCommittees().get(0).getRole());

        assertEquals("Комитет Государственной Думы по обороне", testRecord.getCommittees().get(1).getName());
        assertEquals("CO-EXECUTIVE COMMITTEE", testRecord.getCommittees().get(1).getRole());
    }

    @Test
    void test_850485_7() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("850485-7");

        assertEquals(PASS, testRecord.getBillStatus());
        assertEquals(LocalDate.of(2020, 12, 30), testRecord.getDatePassing());
        assertEquals(true, testRecord.getOriginalLaw());
        assertEquals(OriginType.GOVERNMENT, testRecord.getOriginType());
        assertEquals("О биологической безопасности в Российской Федерации", testRecord.getBillTitle());

        assertEquals(5, testRecord.getCommitteeCount());

        assertEquals("Комитет Государственной Думы по охране здоровья", testRecord.getCommittees().get(0).getName());
        assertEquals("RESPONSIBLE COMMITTEE", testRecord.getCommittees().get(0).getRole());

        assertEquals("Комитет Государственной Думы по безопасности и противодействию коррупции", testRecord.getCommittees().get(1).getName());
        assertEquals("CO-EXECUTIVE COMMITTEE", testRecord.getCommittees().get(1).getRole());

        assertEquals("Комитет Государственной Думы по природным ресурсам, собственности и земельным отношениям", testRecord.getCommittees().get(2).getName());
        assertEquals("CO-EXECUTIVE COMMITTEE", testRecord.getCommittees().get(2).getRole());

        assertEquals("Комитет Государственной Думы по аграрным вопросам", testRecord.getCommittees().get(3).getName());
        assertEquals("CO-EXECUTIVE COMMITTEE", testRecord.getCommittees().get(3).getRole());

        assertEquals("Комитет Государственной Думы по экологии и охране окружающей среды", testRecord.getCommittees().get(4).getName());
        assertEquals("CO-EXECUTIVE COMMITTEE", testRecord.getCommittees().get(4).getRole());

        assertEquals("Комитет Государственной Думы по охране здоровья", testRecord.getCommittees().get(5).getName());
        assertEquals("PROFILE COMMITTEE", testRecord.getCommittees().get(5).getRole());

        assertEquals("Комитет Государственной Думы по безопасности и противодействию коррупции", testRecord.getCommittees().get(6).getName());
        assertEquals("PROFILE COMMITTEE", testRecord.getCommittees().get(6).getRole());

        assertEquals(1, testRecord.getAmendmentCount());
        assertEquals(1, testRecord.getAmendments().size());

        assertEquals("https://sozd.duma.gov.ru/download/F39960FB-6BC4-45CF-B3BF-D4FEC468951B", testRecord.getAmendments().get(0).getTextSourceUrl());
        assertEquals("Комитет Государственной Думы по охране здоровья", testRecord.getAmendments().get(0).getCommitteeName());
        assertEquals(Amendment.Outcome.APPROVED, testRecord.getAmendments().get(0).getOutcome());

        assertEquals(378, testRecord.getAmendments().get(0).getVotesInFavor());
        assertEquals(0, testRecord.getAmendments().get(0).getVotesAgainst());
        assertEquals(1, testRecord.getAmendments().get(0).getVotesAbstention());
    }

    @Test
    void test_204628_7() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("204628-7");

        assertEquals(PASS, testRecord.getBillStatus());
        assertEquals(LocalDate.of(2018, 7, 19), testRecord.getDatePassing());
        assertEquals(false, testRecord.getOriginalLaw());
        assertEquals(OriginType.GOVERNMENT, testRecord.getOriginType());
        assertEquals("О внесении изменений в Федеральный закон \"Об акционерных обществах\"", testRecord.getBillTitle());

        assertEquals(2, testRecord.getCommitteeCount());
        assertEquals("Комитет Государственной Думы по природным ресурсам, собственности и земельным отношениям", testRecord.getCommittees().get(0).getName());
        assertEquals("RESPONSIBLE COMMITTEE", testRecord.getCommittees().get(0).getRole());
        assertEquals("Комитет Государственной Думы по финансовому рынку", testRecord.getCommittees().get(1).getName());
        assertEquals("CO-EXECUTIVE COMMITTEE", testRecord.getCommittees().get(1).getRole());
        assertEquals("Комитет Государственной Думы по природным ресурсам, собственности и земельным отношениям", testRecord.getCommittees().get(2).getName());
        assertEquals("PROFILE COMMITTEE", testRecord.getCommittees().get(2).getRole());

        assertEquals(1, testRecord.getAmendmentCount());
        assertEquals(1, testRecord.getAmendments().size());
        assertEquals("https://sozd.duma.gov.ru/download/BF5AC7D9-C7BE-468E-A7F5-20FC110B6E46", testRecord.getAmendments().get(0).getTextSourceUrl());
        assertEquals("Комитет Государственной Думы по природным ресурсам, собственности и земельным отношениям", testRecord.getAmendments().get(0).getCommitteeName());

        assertEquals(Amendment.Outcome.APPROVED, testRecord.getAmendments().get(0).getOutcome());
        assertEquals(315, testRecord.getAmendments().get(0).getVotesInFavor());
        assertEquals(0, testRecord.getAmendments().get(0).getVotesAgainst());
        assertEquals(1, testRecord.getAmendments().get(0).getVotesAbstention());
    }

    @Test
    void test_160451_6() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("160451-6");

        assertEquals(PASS, testRecord.getBillStatus());
        assertEquals(LocalDate.of(2013, 7, 23), testRecord.getDatePassing());
        assertEquals(false, testRecord.getOriginalLaw());
        assertEquals(OriginType.GROUP_MP, testRecord.getOriginType());
        assertEquals("О внесении изменений в статью 70-1 Земельного кодекса Российской Федерации и Градостроительный кодекс Российской Федерации", testRecord.getBillTitle());

        assertEquals(1, testRecord.getCommitteeCount());
        assertEquals("Комитет Государственной Думы по земельным отношениям и строительству", testRecord.getCommittees().get(0).getName());
        assertEquals("RESPONSIBLE COMMITTEE", testRecord.getCommittees().get(0).getRole());
        assertEquals("Комитет Государственной Думы по земельным отношениям и строительству", testRecord.getCommittees().get(1).getName());
        assertEquals("PROFILE COMMITTEE", testRecord.getCommittees().get(1).getRole());

        assertEquals(3, testRecord.getAmendmentCount());
        assertEquals(3, testRecord.getAmendments().size());

        assertEquals("https://sozd.duma.gov.ru/download/8957B4BD-25F8-4913-AAAE-68690B26EF21", testRecord.getAmendments().get(0).getTextSourceUrl());
        assertEquals("Комитет Государственной Думы по земельным отношениям и строительству", testRecord.getAmendments().get(0).getCommitteeName());

        assertEquals("https://sozd.duma.gov.ru/download/2F1786E8-1F64-4C1C-8668-ACD1DC50109F", testRecord.getAmendments().get(1).getTextSourceUrl());
        assertEquals("Комитет Государственной Думы по земельным отношениям и строительству", testRecord.getAmendments().get(1).getCommitteeName());

        assertEquals("https://sozd.duma.gov.ru/download/ABC66F40-2ABD-4098-ADFE-7594EA6B462C", testRecord.getAmendments().get(2).getTextSourceUrl());
        assertEquals("Комитет Государственной Думы по земельным отношениям и строительству", testRecord.getAmendments().get(2).getCommitteeName());

        assertEquals(Amendment.Outcome.APPROVED, testRecord.getAmendments().get(0).getOutcome());
        assertEquals(315, testRecord.getAmendments().get(0).getVotesInFavor());
        assertEquals(0, testRecord.getAmendments().get(0).getVotesAgainst());
        assertEquals(1, testRecord.getAmendments().get(0).getVotesAbstention());
    }

    @Test
    void test_134824_8() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("134824-8");

        assertEquals(3, testRecord.getStagesCount());
        assertEquals(3, testRecord.getStages().size());

        LegislativeStage stage1 = testRecord.getStages().get(0);

        assertEquals(1, stage1.getStageNumber());
        assertEquals("Внесение законопроекта в Государственную Думу", stage1.getName());
        assertEquals(LocalDate.of(2022, 6, 1), stage1.getDate());

        LegislativeStage stage2 = testRecord.getStages().get(1);

        assertEquals(2, stage2.getStageNumber());
        assertEquals("Предварительное рассмотрение законопроекта, внесенного в Государственную Думу", stage2.getName());
        assertEquals(LocalDate.of(2022, 6, 30), stage2.getDate());

        LegislativeStage stage3 = testRecord.getStages().get(2);

        assertEquals(3, stage3.getStageNumber());
        assertEquals("Рассмотрение законопроекта в первом чтении", stage3.getName());
        assertEquals(LocalDate.of(2023, 6, 14), stage3.getDate());

        assertEquals(2, testRecord.getCommitteeHearingCount());
        assertEquals(LocalDate.of(2022, 6, 2), testRecord.getCommitteeDate());

        assertEquals(1, testRecord.getCommitteeCount());
        assertEquals("Комитет Государственной Думы по просвещению", testRecord.getCommittees().get(0).getName());
        assertEquals("RESPONSIBLE COMMITTEE", testRecord.getCommittees().get(0).getRole());
        assertEquals("Комитет Государственной Думы по просвещению", testRecord.getCommittees().get(1).getName());
        assertEquals("PROFILE COMMITTEE", testRecord.getCommittees().get(1).getRole());
    }

    @Test
    void test_97023448_2() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("97023448-2");

        assertNull(testRecord.getCommitteeDate());
        assertEquals(0, testRecord.getCommitteeCount());
        assertEquals(0, testRecord.getCommittees().size());
    }

    @Test
    void test_96008902_2() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("96008902-2");

        assertNull(testRecord.getDatePassing());
        assertNull(testRecord.getCommitteeDate());
        assertEquals(LegislativeDataRecord.BillStatus.ONGOING, testRecord.getBillStatus());
    }

    @Test
    void test_115864_8() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("115864-8");

        assertEquals(LocalDate.of(2022, 4, 29), testRecord.getCommitteeDate());
        assertEquals(LegislativeDataRecord.BillStatus.PASS, testRecord.getBillStatus());
    }

    @Test
    void test_125874_8() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("125874-8");

        assertEquals(REJECT, testRecord.getBillStatus());
        assertEquals(LocalDate.of(2022, 5, 19), testRecord.getCommitteeDate());
    }

    @Test
    void test_1000396_6() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("1000396-6");

        assertEquals(436, testRecord.getFinalVoteFor());
        assertEquals(0, testRecord.getFinalVoteAgainst());
        assertEquals(0, testRecord.getFinalVoteAbst());
    }

    @Test
    void test_1001390_6() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("1001390-6");

        assertEquals(427, testRecord.getFinalVoteFor());
        assertEquals(0, testRecord.getFinalVoteAgainst());
        assertEquals(0, testRecord.getFinalVoteAbst());
    }

    @Test
    void test_137906_8() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("137906-8");

        assertEquals(3, testRecord.getCommitteeHearingCount());
    }

    @Test
    void test_137677_8() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("137677-8");

        assertEquals(2, testRecord.getCommitteeHearingCount());
    }

    @Test
    void test_49153_6() throws IOException {
        LegislativeDataRecord testRecord = setupAndGetResult("49153-6");

        assertEquals(8, testRecord.getStagesCount());
    }

}
