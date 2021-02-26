package ch.qligier.emed.ocs.schematron;

import ch.qligier.emed.ocs.Utils;
import ch.qligier.emed.ocs.schematron.definition.*;
import ch.qligier.emed.ocs.schematron.exceptions.SchematronParsingException;
import com.helger.commons.io.resource.FileSystemResource;
import com.helger.schematron.sch.TransformerCustomizerSCH;
import com.helger.schematron.sch.SchematronProviderXSLTFromSCH;
import lombok.NonNull;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The preprocessor of Schematron definition files to optimized XSLT files. XSLT files are then used to validate
 * CDA-CH-EMED files.
 *
 * @author Quentin Ligier
 */
public class CdaChEmedSchematronOptimizer {

    /**
     * A pattern that detects nesting predicates duplicating the following descendent selector.
     */
    private static final Pattern DUPLICATED_NESTING_PREDICATE_PATTERN = Pattern.compile("\\[(.+)]/\\1");

    /**
     * A pattern that detects whitespaces around the equal sign of an attribute selector.
     */
    private static final Pattern WHITESPACES_IN_ATTR_SELECTOR_PATTERN = Pattern.compile("(\\[@[a-zA-Z0-9]+)\\s*=\\s*");

    /**
     * Optimizes a Schematron file.
     *
     * @param schematronFile The original Schematron file to convert.
     * @param optimizedFile  The optimized Schematron file to write.
     * @param roleToKeep     The only assert/report role to keep, or {@code null} to disable filtering.
     * @throws IOException                  if any IO error occurs.
     * @throws ParserConfigurationException if the implementation is not available or cannot be instantiated.
     * @throws SAXException                 if any parsing error occurs.
     * @throws SchematronParsingException   if the Schematron file is invalid.
     * @throws TransformerException         if an unrecoverable error occurs during the course of the XML rendering.
     */
    public static void optimizeSchematron(@NonNull final File schematronFile,
                                          @NonNull final File optimizedFile,
                                          @Nullable final String roleToKeep) throws Exception {
        final SchematronParser parser = new SchematronParser();
        final SchematronDefinition definition = parser.parse(schematronFile);

        // Change the role from 'warn' to 'error' from the only assertion of the first pattern.
        // This will force an error if the CCE document is missing template IDs, otherwise this would only be a warning.
        final String ruleId = definition.getPatterns().stream()
            .filter(pattern -> definition.getRulesPerPattern().get(pattern.getId()).size() == 1)
            .findFirst()
            .map(pattern -> definition.getRulesPerPattern().get(pattern.getId()).get(0))
            .orElse(null);
        ((SchematronAssert) definition.getDefinedRules().get(ruleId).getChildren().get(0)).setRole("error");

        for (final SchematronRule rule : definition.getDefinedRules().values()) {
            if (roleToKeep != null) {
                // Remove all reports and all asserts whose role are different than roleToKeep
                rule.setChildren(
                    rule.getChildren().stream()
                        .filter((SchematronRuleChild child) -> {
                            if (child instanceof SchematronAssert) {
                                return roleToKeep.equals(((SchematronAssert) child).getRole());
                            } else if (child instanceof SchematronReport) {
                                return roleToKeep.equals(((SchematronReport) child).getRole());
                            } else {
                                return false;
                            }
                        }).collect(Collectors.toList())
                );
            }

            // Normalize all XPath expression
            if (rule.getContext() != null) {
                rule.setContext(transform(rule.getContext()));
            }
            for (final SchematronRuleChild child : rule.getChildren()) {
                if (child instanceof SchematronAssert) {
                    final SchematronAssert childAssert = (SchematronAssert) child;
                    childAssert.setTest(transform(childAssert.getTest()));
                }
            }
        }

        final SchematronWriter writer = new SchematronWriter();
        writer.writeSchematron(definition, optimizedFile);
    }

    /**
     * Transforms a Schematron file to a 'compiled', XSLT file.
     *
     * @param schematronFile The source Schematron file.
     * @param xsltFile       The destination XSLT file.
     */
    public static void convertToXslt(@NonNull final File schematronFile,
                                     @NonNull final File xsltFile) throws TransformerException, IOException {
        final TransformerCustomizerSCH transformerCustomizer = new TransformerCustomizerSCH();
        final SchematronProviderXSLTFromSCH xsltTransformer =
            new SchematronProviderXSLTFromSCH(new FileSystemResource(schematronFile), transformerCustomizer);

        final Transformer xmlTransformer = Utils.newTransformer();
        xmlTransformer.transform(
            new DOMSource(xsltTransformer.getXSLTDocument()),
            new StreamResult(new FileWriter(xsltFile))
        );
    }

    /**
     * Applies all transformations defined in this class to an XPath expression.
     *
     * @param xpathExpression The XPath expression to transform.
     * @return the fully transformed XPath expression.
     */
    static String transform(@NonNull final String xpathExpression) {
        String xpathFixed = fixWildcardAtStart(xpathExpression);
        xpathFixed = normalizeAttributeSelector(xpathFixed);
        return optimizeDuplicateFilter(xpathFixed);
    }

    /**
     * Fixes XPath expressions that start with a wildcard ('*') by adding the prefix 'anywhere' ('//'). Expressions
     * should start with an axis, not the wildcard step? Saxon at least doesn't match any element with this kind of
     * expression.
     *
     * @param xpathExpression The XPath expresion to fix.
     * @return the fixed XPath expression.
     */
    static String fixWildcardAtStart(@NonNull final String xpathExpression) {
        if (xpathExpression.startsWith("*")) {
            return "//" + xpathExpression;
        } else {
            return xpathExpression;
        }
    }

    /**
     * Optimizes XPath expressions that contain a nesting predicate duplicating the following descendent selector; by
     * example: '//html[body]/body'. In this case the nesting predicate can be dropped, resulting in a clearer, shorter
     * XPath expression. If the XPath engine does not  optimize this too, it should also be a little bit faster.
     *
     * @param xpathExpression The XPath expression to optimize.
     * @return the optimized XPath expression.
     */
    static String optimizeDuplicateFilter(@NonNull final String xpathExpression) {
        final Matcher matcher = DUPLICATED_NESTING_PREDICATE_PATTERN.matcher(xpathExpression);
        if (matcher.find()) {
            // If a duplicated nesting predicate is found, keep only the descendent selector
            return matcher.replaceAll("/$1");
        } else {
            return xpathExpression;
        }
    }

    /**
     * Normalizes the attribute selectors in XPath expressions. It removes whitespaces around the equal sign of an
     * attribute selector; by example in the expression: '*[@root = '1.3.6']'. It is useful to boost the efficiency of
     * {@link #optimizeDuplicateFilter(String)} and should be applied before.
     *
     * @param xpathExpression The XPath expression to normalize.
     * @return the normalized XPath expression.
     */
    static String normalizeAttributeSelector(@NonNull final String xpathExpression) {
        final Matcher matcher = WHITESPACES_IN_ATTR_SELECTOR_PATTERN.matcher(xpathExpression);
        if (matcher.find()) {
            return matcher.replaceAll("$1=");
        } else {
            return xpathExpression;
        }
    }
}
