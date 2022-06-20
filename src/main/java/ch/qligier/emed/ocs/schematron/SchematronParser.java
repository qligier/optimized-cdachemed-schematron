package ch.qligier.emed.ocs.schematron;

import ch.qligier.emed.ocs.Utils;
import ch.qligier.emed.ocs.schematron.exceptions.SchematronParsingException;
import ch.qligier.emed.ocs.schematron.definition.SchematronConstants;
import ch.qligier.emed.ocs.schematron.definition.SchematronDefinition;
import ch.qligier.emed.ocs.schematron.definition.SchematronPattern;
import ch.qligier.emed.ocs.schematron.definition.SchematronRule;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;

/**
 * The parser of Schematron files.
 * <p>
 * When parsing a Schematron file, all but the main phase are ignored.
 *
 * @author Quentin Ligier
 */
@Log
public class SchematronParser {

    /**
     * The {@link Document} builder.
     */
    private final DocumentBuilder documentBuilder;

    /**
     * Creates a parser instance.
     *
     * @throws ParserConfigurationException if a DocumentBuilder cannot be created which satisfies the configuration
     *                                      requested.
     */
    public SchematronParser() throws ParserConfigurationException {
        this.documentBuilder = Utils.newSafeDocumentBuilder();
    }

    /**
     * Parses a Schematron document and returns its definition as an instance of {@link SchematronDefinition}. No
     * transformation operation is applied during the parsing.
     *
     * @param definitionFile The {@link File} instance that points to the Schematron XML file.
     * @return the parsed Schematron definition.
     * @throws IOException                if any IO errors occur.
     * @throws SAXException               if any parse errors occur.
     * @throws SchematronParsingException if the Schematron file is invalid.
     */
    @NonNull
    public SchematronDefinition parse(@NonNull final File definitionFile) throws IOException, SAXException, SchematronParsingException {
        final Document doc = this.documentBuilder.parse(definitionFile);
        final Element root = doc.getDocumentElement();
        final Path rootPath = Paths.get(definitionFile.getParentFile().getAbsolutePath());
        final SchematronDefinition definition = new SchematronDefinition();

        final NodeList nodes = root.getChildNodes();

        /*
         * First pass, we resolve the includes
         */
        for (int i = 0; i < nodes.getLength(); ++i) {
            if (nodes.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element element = (Element) nodes.item(i);
            switch (element.getNodeName()) {
                case SchematronConstants.PATTERN_TAG_NAME -> {
                    final NodeList patternNodes = element.getChildNodes();
                    for (int j = 0; j < patternNodes.getLength(); ++j) {
                        if (patternNodes.item(j).getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        final Element patternChildElement = (Element) patternNodes.item(j);
                        if (SchematronConstants.INCLUDE_TAG_NAME.equals(patternChildElement.getNodeName())) {
                            final String href = patternChildElement.getAttribute("href");
                            if (!href.isEmpty()) {
                                final File includedPatternFile = rootPath.resolve(href).toFile();
                                final Document includedDoc = this.documentBuilder.parse(includedPatternFile);
                                final Element includedDocRoot = includedDoc.getDocumentElement();
                                final Node replacingNode = doc.importNode(includedDocRoot, true);
                                element.replaceChild(replacingNode, patternChildElement);
                            }
                        }
                    }
                }
                case SchematronConstants.INCLUDE_TAG_NAME -> {
                    final String href = element.getAttribute("href");
                    if (href.isEmpty()) {
                        throw new SchematronParsingException("An 'include' element must have a valid 'href' attribute");
                    }
                    final File includedPatternFile = rootPath.resolve(href).toFile();
                    final Document includedDoc = this.documentBuilder.parse(includedPatternFile);
                    final Element includedDocRoot = includedDoc.getDocumentElement();
                    final Node replacingNode = doc.importNode(includedDocRoot, true);
                    root.replaceChild(replacingNode, element);
                }
                default -> {
                }
            }
        }

        /*
         * Second pass, we define namespaces, rules and patterns
         */
        definition.setQueryBinding(root.getAttribute("queryBinding"));
        for (int i = 0; i < nodes.getLength(); ++i) {
            if (nodes.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element element = (Element) nodes.item(i);
            switch (element.getNodeName()) {
                case SchematronConstants.NAMESPACE_TAG_NAME:
                    // This is a new namespace
                    if (element.hasAttribute("prefix") && element.hasAttribute("uri")) {
                        definition.getNamespaces().put(element.getAttribute("prefix"), element.getAttribute("uri"));
                    } else {
                        throw new SchematronParsingException("A 'ns' element is missing its 'prefix' and/or 'uri' attributes");
                    }
                    break;
                case SchematronConstants.PATTERN_TAG_NAME:
                    // This is a new pattern
                    this.parsePatternElementForDefinitions(element, definition);
                    break;
                case SchematronConstants.TITLE_TAG_NAME:
                    // This is the Schematron definition title
                    definition.setTitle(element.getChildNodes().item(0).getNodeValue().strip());
                    break;
                case SchematronConstants.RULE_TAG_NAME:
                    // This is a new rule
                    this.parseRuleElement(element, null, definition);
                    break;
                default:
                    break;
            }
        }

        /*
         * Third pass, we activate rules and patterns
         */
        for (int i = 0; i < nodes.getLength(); ++i) {
            if (nodes.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element element = (Element) nodes.item(i);
            switch (element.getNodeName()) {
                case SchematronConstants.PATTERN_TAG_NAME ->
                    definition.getPatterns().add(SchematronPattern.fromPatternElement(element));
                case SchematronConstants.RULE_TAG_NAME -> {
                    final String ruleId = element.getAttribute("id");
                    if (!definition.getDefinedRules().get(ruleId).isAbstract()) {
                        definition.getEnabledRules().add(ruleId);
                    }
                }
                default -> {
                }
            }
        }

        return definition;
    }

    /**
     * Parses a 'pattern' element and adds it to the definition.
     *
     * @param patternElement The node 'pattern' to parse.
     * @param definition     The Schematron definition.
     * @throws SchematronParsingException if the Schematron file is invalid.
     */
    private void parsePatternElementForDefinitions(@NonNull final Element patternElement,
                                                   @NonNull final SchematronDefinition definition) throws SchematronParsingException {
        final NodeList nodes = patternElement.getChildNodes();
        if (!patternElement.hasAttribute("id")) {
            patternElement.setAttribute("id", "id_" + UUID.randomUUID().toString().replace("-", "_"));
        }
        final String patternId = patternElement.getAttribute("id");

        if (!definition.getRulesPerPattern().containsKey(patternId)) {
            definition.getRulesPerPattern().put(patternId, new ArrayList<>());
        }
        for (int i = 0; i < nodes.getLength(); ++i) {
            if (nodes.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element element = (Element) nodes.item(i);
            if (SchematronConstants.RULE_TAG_NAME.equals(element.getNodeName())) {
                this.parseRuleElement(element, patternId, definition);
            }
        }
    }

    /**
     * Parses a 'rule' element and adds it to the definition.
     *
     * @param ruleElement The node 'rule' to parse.
     * @param patternId   The pattern ID if it has been defined or {@code null}.
     * @param definition  The Schematron definition.
     * @throws SchematronParsingException if the Schematron file is invalid.
     */
    private void parseRuleElement(@NonNull final Element ruleElement,
                                  final String patternId,
                                  @NonNull final SchematronDefinition definition) throws SchematronParsingException {
        if (!ruleElement.hasAttribute("id")) {
            // We generate a rule ID if none was defined
            ruleElement.setAttribute("id", "id_" + UUID.randomUUID().toString().replace("-", "_"));
        }
        final SchematronRule rule = SchematronRule.fromRuleElement(ruleElement, patternId);
        definition.getDefinedRules().put(rule.getId(), rule);
        if (!rule.isAbstract() && patternId != null) {
            definition.getRulesPerPattern().get(patternId).add(rule.getId());
        }
    }
}
