package com.precognox.ceu.legislative_data_collector.hungary

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

//@SpringBootTest
class DebateSizeCollectorTest {

    @Autowired
    lateinit var instance: DebateSizeCollector

    @Test
    @Ignore
    fun test() {
        assertNotNull(instance)

        val testRecord = LegislativeDataRecord()
        testRecord.billId = "T/16223"
        testRecord.billPageUrl = "https://www.parlament.hu/web/guest/iromanyok-elozo-ciklusbeli-adatai" +
                "?p_p_id=hu_parlament_cms_pair_portlet_PairProxy_INSTANCE_9xd2Wc9jP4z8&p_p_lifecycle=1&p_p_state=normal" +
                "&p_p_mode=view&p_auth=krKCBVQM&_hu_parlament_cms_pair_portlet_PairProxy_INSTANCE_9xd2Wc9jP4z8_pairAction=" +
                "%2Finternet%2Fcplsql%2Fogy_irom.irom_adat%3Fp_ckl%3D41%26p_izon%3D16223"

        val result = instance.collectDebateSize(testRecord)

        assertNotNull(result.stages)
        assertTrue(result.stages.size == 4)
        assertTrue(result.stages.find { it.name == "Signing by the president" }?.debateSize == 0)
        assertTrue(result.stages.find { it.name == "Opening of the detailed debate" }?.debateSize == 0)
        assertEquals(true, result.stages.find { it.name == "Debate on the committee reports and on the summary proposal for an amendment" }?.debateSize?.let { it > 100_000 })
        assertEquals(true, result.stages.find { it.name == "Opening of the general debate" }?.debateSize?.let { it > 300_000 })

        assertNotNull(result.plenarySize)
        assertTrue(result.plenarySize > 400_000)
    }

}
