package ch.qligier.emed.ocs.schematron;

import ch.qligier.emed.ocs.schematron.definition.SchematronAssert;
import ch.qligier.emed.ocs.schematron.definition.SchematronDefinition;
import ch.qligier.emed.ocs.schematron.definition.SchematronRule;
import ch.qligier.emed.ocs.schematron.definition.SchematronRuleChild;
import lombok.NonNull;

import java.util.Map;
import java.util.Set;

/**
 * The preprocessor of Schematron definition files for transforming general CDA-CH-EMED rules into
 * ambulatory-specific ones.
 *
 * @author Quentin Ligier
 **/
public class AmbuTransformer implements DefinitionTransformer {

    /**
     * Applies the transformation to the Schematron definition.
     *
     * @param definition The Schematron definition. Mutated.
     */
    public void transform(@NonNull final SchematronDefinition definition) {
        // Remove forbidden rules
        for (final String forbiddenRuleId : getForbiddenRules()) {
            definition.getEnabledRules().remove(forbiddenRuleId);
            definition.getDefinedRules().remove(forbiddenRuleId);
        }

        // Replace value set references
        for (final SchematronRule rule : definition.getDefinedRules().values()) {
            for (final SchematronRuleChild child : rule.getChildren()) {
                if (child instanceof final SchematronAssert asser && asser.getTest().contains("doc('include/voc")) {
                    System.out.println("before");
                    for (final var entry : getValueSetReplacementMap().entrySet()) {
                        asser.setTest(asser.getTest().replace(entry.getKey(), entry.getValue()));
                    }
                    System.out.println("after");
                }
            }
        }
    }

    public static Map<String, String> getValueSetReplacementMap() {
        return Map.of(
            "2.16.756.5.30.1.1.11.2","2.16.756.5.30.1.127.77.12.11.1", // RouteOfAdministration
            "2.16.756.5.30.1.127.77.4.11.2", "2.16.756.5.30.1.127.77.12.11.2", // TimingEvent
            "2.16.756.5.30.1.1.11.83", "2.16.756.5.30.1.127.77.12.11.3" //RegularUnitCode
        );
    }

    public static Set<String> getForbiddenRules() {
        return Set.of(
            "d141e6943-true-d269204e0" // Bad rule in PADV CHANGE, changed PRE
        );
    }
}
