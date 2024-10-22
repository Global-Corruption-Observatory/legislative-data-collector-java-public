package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.*;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JsoupParserTest {

    private JsoupPageParser pageParser = new JsoupPageParser();

    @Test
    public void testBill6322() throws IOException {
        PageSource pageSource = getPageSourceObj("/hungary/test_bill_pages/bill_6322.html");
        LegislativeDataRecord result = pageParser.parseStoredSource(pageSource);

        assertNotNull(result);
        assertEquals(Country.HUNGARY, result.getCountry());
        assertEquals(true, result.getOriginalLaw());
        assertEquals("2019/T/6322", result.getBillId());
        assertEquals("költségvetési törvényjavaslat", result.getBillType());
        assertEquals("Budget bill", result.getTypeOfLawEng());
        assertEquals("LXXI", result.getLawId());
        assertEquals(OriginType.GOVERNMENT, result.getOriginType());
        assertEquals(LegislativeDataRecord.BillStatus.PASS, result.getBillStatus());
        assertEquals(LocalDate.of(2019, 6, 4), result.getDateIntroduction());
        assertEquals(LocalDate.of(2019, 6, 5), result.getCommitteeDate());
        assertEquals(LocalDate.of(2019, 7, 22), result.getDatePassing());
        assertEquals(LocalDate.of(2019, 7, 23), result.getDateEnteringIntoForce());
        assertEquals(15, result.getCommitteeCount());
        assertEquals(13, result.getCommitteeHearingCount());
        assertEquals("https://www.parlament.hu/irom41/06322/06322.htm", result.getBillTextUrl());
        assertEquals("Magyarország 2020. évi központi költségvetéséről", result.getBillTitle());
        assertEquals(LegislativeDataRecord.ProcedureType.REGULAR, result.getProcedureTypeStandard());
        assertEquals("regular", result.getProcedureTypeEng());
        assertEquals("normál", result.getProcedureTypeNational());
        assertEquals(1, result.getOriginators().size());
        assertEquals("kormány (pénzügyminiszter)", result.getOriginators().get(0).getName());
        assertEquals(5, result.getStagesCount());
        assertEquals(5, result.getStages().size());
        assertEquals(127, result.getFinalVoteFor());
        assertEquals(58, result.getFinalVoteAgainst());
        assertEquals(0, result.getFinalVoteAbst());

        checkStagesForBill6322(result.getStages());
        checkCommitteesForBill6322(result.getCommittees());
    }

    @NotNull
    private PageSource getPageSourceObj(String file) throws IOException {
        String pageHtml = IOUtils.toString(getClass().getResourceAsStream(file), StandardCharsets.UTF_8);

        PageSource pageSource = new PageSource();
        pageSource.setRawSource(pageHtml);

        return pageSource;
    }

    private void checkCommitteesForBill6322(List<Committee> committees) {
        checkCommittee(committees, 0, "Költségvetési bizottság", "Procedure of the legislative committee");
        checkCommittee(committees, 1, "Költségvetési bizottság", "Appointed committee");
        checkCommittee(committees, 2, "Gazdasági bizottság", "Committee related to the debate");
        checkCommittee(committees, 3, "Honvédelmi és rendészeti bizottság", "Committee related to the debate");
        checkCommittee(committees, 4, "Igazságügyi bizottság", "Committee related to the debate");
        checkCommittee(committees, 5, "Magyarországi nemzetiségek bizottsága", "Committee related to the debate");
        checkCommittee(committees, 6, "Külügyi bizottság", "Committee related to the debate");
        checkCommittee(committees, 7, "Mezőgazdasági bizottság", "Committee related to the debate");
        checkCommittee(committees, 8, "Nemzetbiztonsági bizottság", "Committee related to the debate");
        checkCommittee(committees, 9, "Nemzeti összetartozás bizottsága", "Committee related to the debate");
        checkCommittee(committees, 10, "Népjóléti bizottság", "Committee related to the debate");
        checkCommittee(committees, 11, "Vállalkozásfejlesztési bizottság", "Committee related to the debate");
        checkCommittee(committees, 12, "Fenntartható fejlődés bizottsága", "Committee related to the debate");
        checkCommittee(committees, 13, "Európai ügyek bizottsága", "Committee related to the debate");
        checkCommittee(committees, 14, "Kulturális bizottság", "Committee related to the debate");
    }

    private void checkCommittee(List<Committee> committees, int index, String name, String role) {
        assertEquals(name, committees.get(index).getName());
        assertEquals(role, committees.get(index).getRole());
    }

    private void checkStagesForBill6322(List<LegislativeStage> stages) {
        checkStage(stages, 0, 1, "Appointing the committee to conduct the detailed debate", LocalDate.of(2019, 6, 5));
        checkStage(stages, 1, 2, "Opening of the general debate", LocalDate.of(2019, 6, 19));
        checkStage(stages, 2, 3, "Opening of the detailed debate", LocalDate.of(2019, 6, 24));
        checkStage(stages, 3, 4, "Debate on the committee reports and on the summary proposal for an amendment", LocalDate.of(2019, 7, 8));
        checkStage(stages, 4, 5, "Signing by the president", LocalDate.of(2019, 7, 22));
    }

    private void checkStage(List<LegislativeStage> stages, int index, int number, String name, LocalDate date) {
        assertEquals(number, stages.get(index).getStageNumber());
        assertEquals(name, stages.get(index).getName());
        assertEquals(date, stages.get(index).getDate());
    }

    @Test
    void testBill10377() throws IOException {
        PageSource pageSource = getPageSourceObj("/hungary/test_bill_pages/bill_10377.html");
        LegislativeDataRecord result = pageParser.parseStoredSource(pageSource);

        assertNotNull(result);
        assertEquals(Country.HUNGARY, result.getCountry());
        assertEquals("2016/T/10377", result.getBillId());
        assertEquals("Magyarország 2017. évi központi költségvetéséről", result.getBillTitle());
        assertEquals("költségvetési törvényjavaslat", result.getBillType());
        assertEquals("Budget bill", result.getTypeOfLawEng());
        assertEquals(Boolean.TRUE, result.getOriginalLaw());
        assertEquals(LocalDate.of(2016, 4, 26), result.getDateIntroduction());
        assertEquals(LegislativeDataRecord.BillStatus.PASS, result.getBillStatus());
        assertEquals("XC", result.getLawId());
        assertEquals(LocalDate.of(2016, 6, 24), result.getDateEnteringIntoForce());
        assertEquals(OriginType.GOVERNMENT, result.getOriginType());
        assertEquals(1, result.getOriginators().size());
        assertEquals("kormány (nemzetgazdasági miniszter)", result.getOriginators().get(0).getName());
        assertEquals("https://www.parlament.hu/irom40/10377/10377.htm", result.getBillTextUrl());
        assertEquals(5, result.getStagesCount());
        assertEquals(LocalDate.of(2016, 4, 27), result.getCommitteeDate());
        assertEquals(LocalDate.of(2016, 6, 21), result.getDatePassing());
        assertEquals(15, result.getCommitteeCount());
        assertEquals(13, result.getCommitteeHearingCount());
        //assertEquals("https://www.parlament.hu/irom40/10377/10377-1172.pdf", result.getLawTextUrl());
        assertEquals("process deviating from house rules", result.getProcedureTypeEng());
        assertEquals("határozati házszabályi rendelkezésektől való eltéréssel", result.getProcedureTypeNational());
        assertEquals(122, result.getFinalVoteFor());
        assertEquals(63, result.getFinalVoteAgainst());
        assertEquals(0, result.getFinalVoteAbst());

        checkStagesForBill10377(result.getStages());
        checkCommitteesForBill10377(result.getCommittees());
    }

    private void checkStagesForBill10377(List<LegislativeStage> stages) {
        checkStage(stages, 0, 1, "Appointing the committee to conduct the detailed debate", LocalDate.of(2016, 4, 27));
        checkStage(stages, 1, 2, "Opening of the general debate", LocalDate.of(2016, 5, 11));
        checkStage(stages, 2, 3, "Opening of the detailed debate", LocalDate.of(2016, 5, 17));
        checkStage(stages, 3, 4, "Debate on the committee reports and on the summary proposal for an amendment", LocalDate.of(2016, 6, 6));
        checkStage(stages, 4, 5, "Signing by the president", LocalDate.of(2016, 6, 21));
    }

    private void checkCommitteesForBill10377(List<Committee> committees) {
        checkCommittee(committees, 0, "Költségvetési bizottság", "Procedure of the legislative committee");
        checkCommittee(committees, 1, "Költségvetési bizottság", "Appointed committee");
        checkCommittee(committees, 2, "Gazdasági bizottság", "Committee related to the debate");
        checkCommittee(committees, 3, "Honvédelmi és rendészeti bizottság", "Committee related to the debate");
        checkCommittee(committees, 4, "Igazságügyi bizottság", "Committee related to the debate");
        checkCommittee(committees, 5, "Kulturális bizottság", "Committee related to the debate");
        checkCommittee(committees, 6, "Külügyi bizottság", "Committee related to the debate");
        checkCommittee(committees, 7, "Mezőgazdasági bizottság", "Committee related to the debate");
        checkCommittee(committees, 8, "Nemzetbiztonsági bizottság", "Committee related to the debate");
        checkCommittee(committees, 9, "Nemzeti összetartozás bizottsága", "Committee related to the debate");
        checkCommittee(committees, 10, "Népjóléti bizottság", "Committee related to the debate");
        checkCommittee(committees, 11, "Vállalkozásfejlesztési bizottság", "Committee related to the debate");
        checkCommittee(committees, 12, "Magyarországi nemzetiségek bizottsága", "Committee related to the debate");
        checkCommittee(committees, 13, "Európai ügyek bizottsága", "Committee related to the debate");
        checkCommittee(committees, 14, "Fenntartható fejlődés bizottsága", "Committee related to the debate");
    }

    @Test
    void testBill503() throws IOException {
        PageSource pageSource = getPageSourceObj("/hungary/test_bill_pages/bill_503.html");
        LegislativeDataRecord result = pageParser.parseStoredSource(pageSource);

        assertNotNull(result);
        assertEquals(Country.HUNGARY, result.getCountry());
        assertEquals("2018/T/503", result.getBillId());
        assertEquals("Magyarország 2019. évi központi költségvetéséről", result.getBillTitle());
        assertEquals("költségvetési törvényjavaslat", result.getBillType());
        assertEquals("Budget bill", result.getTypeOfLawEng());
        assertEquals(Boolean.TRUE, result.getOriginalLaw());
        assertEquals(LocalDate.of(2018, 6, 13), result.getDateIntroduction());
        assertEquals(LegislativeDataRecord.BillStatus.PASS, result.getBillStatus());
        assertEquals("L", result.getLawId());
        assertEquals(LocalDate.of(2018, 7, 31), result.getDateEnteringIntoForce());
        assertEquals(OriginType.GOVERNMENT, result.getOriginType());
        assertEquals(1, result.getOriginators().size());
        assertEquals("kormány (pénzügyminiszter)", result.getOriginators().get(0).getName());
        assertEquals("https://www.parlament.hu/irom41/00503/00503.htm", result.getBillTextUrl());
        assertEquals(5, result.getStagesCount());
        assertEquals(LocalDate.of(2018, 6, 14), result.getCommitteeDate());
        assertEquals(LocalDate.of(2018, 7, 30), result.getDatePassing());
        assertEquals(15, result.getCommitteeCount());
        assertEquals(13, result.getCommitteeHearingCount());
        //assertEquals("https://www.parlament.hu/irom40/10377/10377-1172.pdf", result.getLawTextUrl());
        assertEquals("process deviating from house rules", result.getProcedureTypeEng());
        assertEquals("határozati házszabályi rendelkezésektől való eltéréssel", result.getProcedureTypeNational());
        assertEquals(128, result.getFinalVoteFor());
        assertEquals(56, result.getFinalVoteAgainst());
        assertEquals(0, result.getFinalVoteAbst());

        checkStagesForBill503(result.getStages());
        checkCommitteesForBill503(result.getCommittees());
    }

    private void checkStagesForBill503(List<LegislativeStage> stages) {
        checkStage(stages, 0, 1, "Appointing the committee to conduct the detailed debate", LocalDate.of(2018, 6, 14));
        checkStage(stages, 1, 2, "Opening of the general debate", LocalDate.of(2018, 6, 27));
        checkStage(stages, 2, 3, "Opening of the detailed debate", LocalDate.of(2018, 7, 2));
        checkStage(stages, 3, 4, "Debate on the committee reports and on the summary proposal for an amendment", LocalDate.of(2018, 7, 16));
        checkStage(stages, 4, 5, "Signing by the president", LocalDate.of(2018, 7, 30));
    }

    private void checkCommitteesForBill503(List<Committee> committees) {
        checkCommittee(committees, 0, "Költségvetési bizottság", "Procedure of the legislative committee");
        checkCommittee(committees, 1, "Költségvetési bizottság", "Appointed committee");
        checkCommittee(committees, 2, "Gazdasági bizottság", "Committee related to the debate");
        checkCommittee(committees, 3, "Honvédelmi és rendészeti bizottság", "Committee related to the debate");
        checkCommittee(committees, 4, "Igazságügyi bizottság", "Committee related to the debate");
        checkCommittee(committees, 5, "Magyarországi nemzetiségek bizottsága", "Committee related to the debate");
        checkCommittee(committees, 6, "Külügyi bizottság", "Committee related to the debate");
        checkCommittee(committees, 7, "Mezőgazdasági bizottság", "Committee related to the debate");
        checkCommittee(committees, 8, "Nemzetbiztonsági bizottság", "Committee related to the debate");
        checkCommittee(committees, 9, "Nemzeti összetartozás bizottsága", "Committee related to the debate");
        checkCommittee(committees, 10, "Népjóléti bizottság", "Committee related to the debate");
        checkCommittee(committees, 11, "Vállalkozásfejlesztési bizottság", "Committee related to the debate");
        checkCommittee(committees, 12, "Fenntartható fejlődés bizottsága", "Committee related to the debate");
        checkCommittee(committees, 13, "Európai ügyek bizottsága", "Committee related to the debate");
        checkCommittee(committees, 14, "Kulturális bizottság", "Committee related to the debate");
    }

}
