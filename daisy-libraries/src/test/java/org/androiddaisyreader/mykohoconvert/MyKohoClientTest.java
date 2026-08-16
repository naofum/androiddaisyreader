package org.androiddaisyreader.mykohoconvert;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MyKohoClientTest {

    @Test
    void parseMunicipalitiesXml_parsesEntries() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<municipalities>\n"
                + "  <municipality>\n"
                + "    <name>北海道森町</name>\n"
                + "    <code>013455</code>\n"
                + "    <issueId>269040</issueId>\n"
                + "    <uri>mykoho://013455/269040</uri>\n"
                + "  </municipality>\n"
                + "  <municipality>\n"
                + "    <name>小平市（声のたより）</name>\n"
                + "    <code>132110</code>\n"
                + "    <issueId></issueId>\n"
                + "    <uri>voicepage://www.city.kodaira.tokyo.jp/shihou-voice/</uri>\n"
                + "  </municipality>\n"
                + "</municipalities>\n";

        List<MyKohoInfo> result = MyKohoClient.parseMunicipalitiesXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, result.size());

        assertEquals("北海道森町", result.get(0).getMunicipality());
        assertEquals("013455", result.get(0).getLgCode());
        assertEquals("269040", result.get(0).getMyKohoCode());
        assertEquals("mykoho://013455/269040", result.get(0).getLgUrl());

        assertEquals("小平市（声のたより）", result.get(1).getMunicipality());
        assertEquals("132110", result.get(1).getLgCode());
        assertEquals("voicepage://www.city.kodaira.tokyo.jp/shihou-voice/", result.get(1).getLgUrl());
    }

    @Test
    void parseMunicipalitiesXml_derivesLgUrlWhenUriEmpty() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<municipalities>\n"
                + "  <municipality>\n"
                + "    <name>空URI市</name>\n"
                + "    <code>012345</code>\n"
                + "    <issueId>67890</issueId>\n"
                + "    <uri></uri>\n"
                + "  </municipality>\n"
                + "</municipalities>\n";

        List<MyKohoInfo> result = MyKohoClient.parseMunicipalitiesXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, result.size());
        assertEquals("mykoho://012345/67890", result.get(0).getLgUrl());
    }

    @Test
    void parseMunicipalitiesXml_skipsEmptyNameOrCode() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<municipalities>\n"
                + "  <municipality>\n"
                + "    <name></name>\n"
                + "    <code>013455</code>\n"
                + "    <issueId>1</issueId>\n"
                + "    <uri></uri>\n"
                + "  </municipality>\n"
                + "  <municipality>\n"
                + "    <name>コードなし市</name>\n"
                + "    <code></code>\n"
                + "    <issueId>2</issueId>\n"
                + "    <uri></uri>\n"
                + "  </municipality>\n"
                + "  <municipality>\n"
                + "    <name>正常市</name>\n"
                + "    <code>013456</code>\n"
                + "    <issueId>3</issueId>\n"
                + "    <uri></uri>\n"
                + "  </municipality>\n"
                + "</municipalities>\n";

        List<MyKohoInfo> result = MyKohoClient.parseMunicipalitiesXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, result.size());
        assertEquals("正常市", result.get(0).getMunicipality());
    }
}
