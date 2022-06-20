package ch.qligier.emed.ocs.schematron;

import ch.qligier.emed.ocs.Utils;
import ch.qligier.emed.ocs.schematron.definition.*;
import lombok.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * A specialized class that can write an optimized Schematron file from a regular Schematron definition.
 *
 * @author Quentin Ligier
 */
@SuppressWarnings("squid:S1905")
public class SchematronWriter {

    /**
     * The Schematron document builder.
     */
    private final DocumentBuilder documentBuilder;

    /**
     * The transformer of {@link Document} to a stream of the rendered XML content.
     */
    private final Transformer xmlTransformer;

    /**
     * Constructor.
     *
     * @throws ParserConfigurationException      if the implementation is not available or cannot be instantiated.
     * @throws TransformerConfigurationException if the implementation is not available or cannot be instantiated.
     */
    public SchematronWriter() throws ParserConfigurationException, TransformerConfigurationException {
        this.documentBuilder = Utils.newSafeDocumentBuilder();
        this.xmlTransformer = Utils.newTransformer();
    }

    /**
     * Writes a Schematron definition as a specialized file. Transformations occur to conform to the Schematron
     * specificity.
     *
     * @param definition      The original Schematron definition
     * @param destinationFile The optimized file that will be written.
     * @throws TransformerException if an unrecoverable error occurs during the course of the XML rendering.
     */
    public void writeSchematron(@NonNull final SchematronDefinition definition,
                                @NonNull final File destinationFile) throws TransformerException {
        if ((destinationFile.exists() && !Files.isWritable(destinationFile.toPath()))
            || !Files.isWritable(destinationFile.getParentFile().toPath())) {
            throw new IllegalArgumentException("The destination file is not writable");
        }

        final Document document = this.documentBuilder.newDocument();
        final Element rootElement = document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.ROOT_TAG_NAME);
        rootElement.setAttribute("queryBinding", definition.getQueryBinding());
        document.appendChild(rootElement);

        // Add the title if it was defined
        if (definition.getTitle() != null) {
            final Element titleElement =
                document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.TITLE_TAG_NAME);
            titleElement.setTextContent(definition.getTitle());
            rootElement.appendChild(titleElement);
        }

        // Add namespaces, they don't need any transformation
        for (final Map.Entry<String, String> namespace : definition.getNamespaces().entrySet()) {
            final Element namespaceElement =
                document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.NAMESPACE_TAG_NAME);
            namespaceElement.setAttribute("prefix", namespace.getKey());
            namespaceElement.setAttribute("uri", namespace.getValue());
            rootElement.appendChild(namespaceElement);
        }

        // Add patterns and rules
        for (final SchematronPattern pattern : definition.getPatterns()) {
            final Element patternElement =
                document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.PATTERN_TAG_NAME);
            patternElement.setAttribute("id", pattern.getId());

            for (final SchematronRule rule : getOptimizedRulesForPattern(definition, pattern.getId())) {
                final Element ruleElement =
                    document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.RULE_TAG_NAME);
                ruleElement.setAttribute("id", rule.getId());
                ruleElement.setAttribute("context", rule.getContext());

                for (final SchematronRuleChild child : rule.getChildren()) {
                    if (child instanceof final SchematronAssert schematronAssert) {
                        final Element assertElement =
                            document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.ASSERT_TAG_NAME);
                        assertElement.setAttribute("test", schematronAssert.getTest());
                        assertElement.setAttribute("role", schematronAssert.getRole());
                        for (final Node messageNode : schematronAssert.getMessageNodes()) {
                            assertElement.appendChild(document.importNode(messageNode, true));
                        }
                        ruleElement.appendChild(assertElement);
                    } else if (child instanceof final SchematronReport schematronReport) {
                        final Element reportElement =
                            document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.REPORT_TAG_NAME);
                        reportElement.setAttribute("test", schematronReport.getTest());
                        reportElement.setAttribute("role", schematronReport.getRole());
                        for (final Node messageNode : schematronReport.getMessageNodes()) {
                            reportElement.appendChild(document.importNode(messageNode, true));
                        }
                        ruleElement.appendChild(reportElement);
                    } else if (child instanceof final SchematronLet schematronLet) {
                        final Element letElement =
                            document.createElementNS(SchematronConstants.SCHEMATRON_NAMESPACE, SchematronConstants.LET_TAG_NAME);
                        letElement.setAttribute("name", schematronLet.getName());
                        letElement.setAttribute("value", schematronLet.getValue());
                        ruleElement.appendChild(letElement);
                    }
                }

                // Only add the rule if it's not empty
                if (ruleElement.getChildNodes().getLength() > 0) {
                    patternElement.appendChild(ruleElement);
                }
            }

            // Only add the pattern if it's not empty
            if (patternElement.getChildNodes().getLength() > 0) {
                rootElement.appendChild(patternElement);
            }
        }

        // Write result to file
        this.xmlTransformer.transform(new DOMSource(document), new StreamResult(destinationFile));
    }

    /**
     * Returns the optimized rules that are contained in a pattern.
     *
     * @param definition The original Schematron definition.
     * @param patternId  The pattern ID.
     * @return a list of optimized Schematron rules.
     */
    @NonNull
    private List<SchematronRule> getOptimizedRulesForPattern(@NonNull final SchematronDefinition definition,
                                                             @NonNull final String patternId) {
        if (!definition.getRulesPerPattern().containsKey(patternId)) {
            throw new IllegalArgumentException("The pattern cannot be found in the definition");
        }

        // List of rules to process and return
        return definition.getRulesPerPattern().get(patternId).stream()
            .map(ruleId -> definition.getDefinedRules().get(ruleId))
            .filter(Objects::nonNull)
            .filter(rule -> !rule.isAbstract())
            .map(rule -> optimizeRule(definition, rule))
            .toList();
    }

    /**
     * Transforms a regular rule to an optimized rule by resolving extends, transforming XPath expressions and removing
     * non-error assertions.
     *
     * @param definition The original Schematron definition.
     * @param rule       The rule to optimize.
     * @return the transformed rule.
     */
    @NonNull
    private SchematronRule optimizeRule(@NonNull final SchematronDefinition definition,
                                        @NonNull final SchematronRule rule) {
        final SchematronRule optimizeRule = rule.clone();
        // Remove children that are 'extends' elements and replace them by their own list of children
        optimizeRule.setChildren(resolveExtendedChildren(definition, rule));
        return optimizeRule;
    }

    /**
     * Resolves the list of children of an extended rule.
     *
     * @param definition The original Schematron definition.
     * @param rule       The extended rule.
     * @return the list of children of the parent rule and all extended rules.
     */
    @NonNull
    private List<SchematronRuleChild> resolveExtendedChildren(@NonNull final SchematronDefinition definition,
                                                              @NonNull final SchematronRule rule) {
        final List<SchematronRuleChild> children = new ArrayList<>(rule.getChildren());
        if (children.stream().anyMatch(SchematronExtends.class::isInstance)) {
            final int index = Utils.listIndexOf(children, SchematronExtends.class::isInstance);
            final SchematronExtends extend = (SchematronExtends) children.get(index);
            children.remove(index);
            final List<SchematronRuleChild> newChildren =
                resolveExtendedChildren(definition, definition.getDefinedRules().get(extend.getExtendsRuleId()));
            final ListIterator<SchematronRuleChild> listIterator = newChildren.listIterator(newChildren.size());
            while (listIterator.hasPrevious()) {
                children.add(index, listIterator.previous());
            }
        }
        return children;
    }
}
