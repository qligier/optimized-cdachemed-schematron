package ch.qligier.emed.ocs.schematron;

import ch.qligier.emed.ocs.schematron.definition.SchematronDefinition;
import lombok.NonNull;

/**
 * A Schematron definition transformer.
 *
 * @author Quentin Ligier
 **/
public interface DefinitionTransformer {

    /**
     * Applies the transformation to the Schematron definition.
     *
     * @param definition The Schematron definition. Mutated.
     */
    void transform(@NonNull final SchematronDefinition definition);
}
