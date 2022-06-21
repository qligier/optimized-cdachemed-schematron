package ch.qligier.emed.ocs;

import ch.qligier.emed.ocs.schematron.AmbuTransformer;
import ch.qligier.emed.ocs.schematron.CdaChEmedSchematronOptimizer;
import ch.qligier.emed.ocs.schematron.DefinitionTransformer;
import lombok.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 */
public class OptimizedSchematronConverter {

    /**
     * Directory in which to read the Schematron files.
     */
    private static final String SCHEMATRON_INPUT_DIR = "input/0.98.0/";

    /**
     * Directory in which to write the XSLT files.
     */
    private static final String SCHEMATRON_OUTPUT_DIR = "dist/0.98.0/";

    /**
     * List of CDA-CH-EMED Schematron source files.
     */
    private static final List<String> SCHEMATRON_FILES = List.of(
        "cdachemed-MTP", "cdachemed-PRE", "cdachemed-DIS", "cdachemed-PADV", "cdachemed-PML", "cdachemed-PMLC"
    );

    private static final String INCLUDE_DIR = "include/";

    private static final Logger LOG = Logger.getLogger(OptimizedSchematronConverter.class.getName());

    /**
     * @param args
     */
    public static void main(final String[] args) throws Exception {
        final File outputDir = new File(SCHEMATRON_OUTPUT_DIR);
        if (outputDir.isDirectory()) {
            Files.delete(outputDir.toPath());

        }
        Files.createDirectories(outputDir.toPath());

        final List<DefinitionTransformer> definitionTransformers = List.of(
            new AmbuTransformer()
        );

        copyIncludes();
        for (final String schematronFilename : SCHEMATRON_FILES) {
            final File schematronFile = Path.of(SCHEMATRON_INPUT_DIR, schematronFilename + ".sch").toFile();
            optimizeSchematronFile(
                schematronFile,
                Path.of(SCHEMATRON_OUTPUT_DIR, schematronFilename + "-all.xslt").toFile(),
                definitionTransformers,
                null);
            optimizeSchematronFile(
                schematronFile,
                Path.of(SCHEMATRON_OUTPUT_DIR, schematronFilename + "-error.xslt").toFile(),
                definitionTransformers,
                "error");
        }
        cleanIncludes();
        LOG.info("End of conversion");
    }

    /**
     * Ensures that a specific Schematron file exists; tries to create it by converting the Schematron file if it does
     * not.
     *
     * @param schematronFile The source Schematron file.
     * @param xsltFile       The target XSLT file.
     * @throws Exception if the Schematron file is missing or the transformation fails.
     */
    private static void optimizeSchematronFile(@NonNull final File schematronFile,
                                               @NonNull final File xsltFile,
                                               @NonNull final List<DefinitionTransformer> definitionTransformers,
                                               final String roleToKeep) throws Exception {
        LOG.info("- Transforming " + schematronFile.getName());
        final File optimizedSchematronFile = File.createTempFile("cdachemed_", "_sch");
        optimizedSchematronFile.deleteOnExit();
        if (!schematronFile.isFile() || !schematronFile.canRead()) {
            throw new FileNotFoundException("The Schematron file cannot be found: " + schematronFile);
        }

        LOG.info("  + Optimizing the Schematron definition");
        CdaChEmedSchematronOptimizer.optimizeSchematron(schematronFile, optimizedSchematronFile,
            definitionTransformers, roleToKeep);
        LOG.info("  + Converting it to XSLT");
        CdaChEmedSchematronOptimizer.convertToXslt(optimizedSchematronFile, xsltFile);
        Files.delete(optimizedSchematronFile.toPath());
        LOG.info("  + Done");
    }

    /**
     *
     * @throws IOException
     */
    private static void copyIncludes() throws IOException {
        LOG.info("- Copying 'includes/' directory");
        final File srcDirectory = Path.of(SCHEMATRON_INPUT_DIR, INCLUDE_DIR).toFile();
        final File distDirectory = Path.of(SCHEMATRON_OUTPUT_DIR, INCLUDE_DIR).toFile();
        Files.createDirectories(distDirectory.toPath());
        for (final File srcFile : Utils.listFiles(srcDirectory)) {
            Files.copy(srcFile.toPath(), Path.of(SCHEMATRON_OUTPUT_DIR, INCLUDE_DIR, srcFile.getName()));
        }
    }

    /**
     * Clean the 'include/' directory by deleting all files that are not used by the Schematron.
     */
    private static void cleanIncludes() throws IOException {
        LOG.info("- Cleaning the 'include/' directory");

        final List<String> xsltContents = Utils.listFiles(new File(SCHEMATRON_OUTPUT_DIR)).stream()
            .filter(file -> file.getName().endsWith(".xslt"))
            .map(Utils::readFileToString).toList();

        final File dir = new File(SCHEMATRON_OUTPUT_DIR + INCLUDE_DIR);
        int nbDeletedFiles = 0;
        for (final File includeFile : Utils.listFiles(dir)) {
            // Only the value set files (starting with "voc-") are still linked
            if (!includeFile.getName().startsWith("voc-")) {
                Files.delete(includeFile.toPath());
                ++nbDeletedFiles;
                continue;
            }

            // We check if the file is actually included in any XSLT file
            boolean deleteInclude = true;
            for (final String xsltContent : xsltContents) {
                if (xsltContent.contains(includeFile.getName())) {
                    deleteInclude = false;
                    break;
                }
            }

            if (deleteInclude) {
                Files.delete(includeFile.toPath());
                ++nbDeletedFiles;
            }
        }
        LOG.info("  + Cleaned " + nbDeletedFiles + " useless includes");
    }
}
