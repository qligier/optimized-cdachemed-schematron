package ch.qligier.emed.ocs.schematron;

import ch.qligier.emed.ocs.Utils;
import ch.qligier.emed.ocs.schematron.definition.SchematronDefinition;
import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import java.io.File;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The test bed for {@link SchematronWriter}.
 */
class SchematronWriterTest {

    private final ClassLoader classLoader = getClass().getClassLoader();
    private final static String RES_DIR = "schematron/converter/";

    private final SchematronParser schematronParser;
    private final SchematronWriter schematronWriter;
    private final DocumentBuilder documentBuilder;

    SchematronWriterTest() throws ParserConfigurationException, TransformerConfigurationException {
        this.schematronParser = new SchematronParser();
        this.schematronWriter = new SchematronWriter();
        this.documentBuilder = Utils.newSafeDocumentBuilder();
    }

    @Test
    void testSample1() throws Exception {
        final File definitionFile = loadResource("schematron1.sch");
        final SchematronDefinition definition = this.schematronParser.parse(definitionFile);

        final File tempFile = File.createTempFile("schematron_", ".xml");
        this.schematronWriter.writeSchematron(definition, tempFile);
        final Document doc = this.documentBuilder.parse(tempFile);
        tempFile.delete();

        assertEquals(2, doc.getElementsByTagName("pattern").getLength());
        assertEquals(4, doc.getElementsByTagName("rule").getLength());
        assertEquals(12, doc.getElementsByTagName("assert").getLength());
        assertEquals(
            "*[substance[template[@root = '1']]]/substance[template[@root = '1]]/template[@root='1']",
            getNthElementByTagName(doc, "rule", 0).getAttribute("context")
        );
    }

    /**
     * Test that the order of pattern is the same than it the reference XSL file.
     */
    @Test
    void testSample2() throws Exception {
        final File definitionFile = loadResource("schematron2.sch");
        final SchematronDefinition definition = this.schematronParser.parse(definitionFile);

        final File tempFile = File.createTempFile("schematron_", ".xml");
        this.schematronWriter.writeSchematron(definition, tempFile);
        final Document doc = this.documentBuilder.parse(tempFile);
        tempFile.delete();

        assertEquals(5, doc.getElementsByTagName("pattern").getLength());
        assertEquals(5, doc.getElementsByTagName("rule").getLength());
        assertEquals("p1", getNthElementByTagName(doc, "pattern", 0).getAttribute("id"));
        assertEquals("p2", getNthElementByTagName(doc, "pattern", 1).getAttribute("id"));
        assertEquals("p3", getNthElementByTagName(doc, "pattern", 2).getAttribute("id"));
        assertEquals("p4", getNthElementByTagName(doc, "pattern", 3).getAttribute("id"));
        assertEquals("p6", getNthElementByTagName(doc, "pattern", 4).getAttribute("id"));
    }

    /**
     * Test that the order of pattern is the same than it the reference XSL file.
     */
    @Test
    void testSample3() throws Exception {
        final File definitionFile = loadResource("schematron3.sch");
        final SchematronDefinition definition = this.schematronParser.parse(definitionFile);

        final File tempFile = File.createTempFile("schematron_", ".xml");
        this.schematronWriter.writeSchematron(definition, tempFile);
        final Document doc = this.documentBuilder.parse(tempFile);
        tempFile.delete();

        assertEquals("xslt2", doc.getDocumentElement().getAttribute("queryBinding"));

        assertEquals(1, doc.getElementsByTagName("title").getLength());
        assertEquals("Simple Schematron definition", doc.getElementsByTagName("title").item(0).getTextContent());

        assertEquals(2, doc.getElementsByTagName("ns").getLength());

        assertEquals(1, doc.getElementsByTagName("pattern").getLength());
        assertEquals("pattern1", getNthElementByTagName(doc, "pattern", 0).getAttribute("id"));
        assertEquals(1, doc.getElementsByTagName("rule").getLength());
        assertEquals("rule3", getNthElementByTagName(doc, "rule", 0).getAttribute("id"));

        final NodeList ruleChildren = doc.getElementsByTagName("rule").item(0).getChildNodes();
        assertEquals(6, ruleChildren.getLength());
        assertEquals("assert", ruleChildren.item(0).getNodeName());
        assertEquals("test1.1", ruleChildren.item(0).getAttributes().getNamedItem("test").getNodeValue());
        assertEquals("assert", ruleChildren.item(1).getNodeName());
        assertEquals("test2.1", ruleChildren.item(1).getAttributes().getNamedItem("test").getNodeValue());
        assertEquals("let", ruleChildren.item(2).getNodeName());
        assertEquals("var2.2", ruleChildren.item(2).getAttributes().getNamedItem("name").getNodeValue());
        assertEquals("'Variable 2.2'", ruleChildren.item(2).getAttributes().getNamedItem("value").getNodeValue());
        assertEquals("assert", ruleChildren.item(3).getNodeName());
        assertEquals("test2.3", ruleChildren.item(3).getAttributes().getNamedItem("test").getNodeValue());
        assertEquals("report", ruleChildren.item(4).getNodeName());
        assertEquals("test2.4", ruleChildren.item(4).getAttributes().getNamedItem("test").getNodeValue());
        assertEquals("assert", ruleChildren.item(5).getNodeName());
        assertEquals("test3.1", ruleChildren.item(5).getAttributes().getNamedItem("test").getNodeValue());
    }

    private File loadResource(@NonNull final String resourceName) {
        return new File(Objects.requireNonNull(this.classLoader.getResource(RES_DIR + resourceName)).getFile());
    }

    private Element getNthElementByTagName(@NonNull Document document,
                                           @NonNull final String tagName,
                                           final int nth) {
        final NodeList nodes = document.getElementsByTagName(tagName);
        if (nth < 0 || nth >= nodes.getLength()) {
            throw new IllegalArgumentException("The nth tag is not found");
        }
        return (Element) nodes.item(nth);
    }
}
