//package com.precognox.ceu.legislative_data_collector;
//
//import com.jauntium.Browser;
//import com.jauntium.Document;
//import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
//import org.junit.jupiter.api.Test;
//import org.openqa.selenium.chrome.ChromeDriver;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//
//@SpringBootTest
//class CeuLegislativeDataCollectorApplicationTests {
//
//    @Test
//    void contextLoads() {
//    }
//
//    @Test
//    void testSingleBill() {
//        String page = "https://www.parlament.hu/web/guest/iromanyok-egyszerusitett-lekerdezese?p_p_id=hu_parlament_cms_pair_portlet_PairProxy_INSTANCE_9xd2Wc9jP4z8&p_p_lifecycle=1&p_p_state=normal&p_p_mode=view&p_auth=iD92uvHi&_hu_parlament_cms_pair_portlet_PairProxy_INSTANCE_9xd2Wc9jP4z8_pairAction=%2Finternet%2Fcplsql%2Fogy_irom.irom_adat%3Fp_ckl%3D41%26p_izon%3D15989";
//
//        Browser browser = new Browser(new ChromeDriver());
//        Document doc = browser.visit(page);
//
////        HungaryDataCollector coll = new HungaryDataCollector();
////        LegislativeDataRecord record = coll.buildRecord(doc);
////
////        assertNotNull(record);
//    }
//}
