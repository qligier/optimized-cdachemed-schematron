package ch.qligier.emed.ocs;

import ch.qligier.emed.ocs.schematron.CdaChEmedSchematronOptimizer;
import lombok.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
public class OptimizedSchematronConverter {

    /**
     * Directory in which to read the Schematron files.
     */
    private static final String SCHEMATRON_INPUT_DIR = "input/0.96.1/";

    /**
     * Directory in which to write the XSLT files.
     */
    private static final String SCHEMATRON_OUTPUT_DIR = "dist/0.96.1/";

    /**
     * List of CDA-CH-EMED Schematron source files.
     */
    private static final List<String> SCHEMATRON_FILES = List.of(
        "cdachemed-MTP", "cdachemed-PRE", "cdachemed-DIS", "cdachemed-PADV", "cdachemed-PML", "cdachemed-PMLC"
    );

    /**
     * @param args
     */
    public static void main(final String[] args) throws Exception {
        final File outputDir = new File(SCHEMATRON_OUTPUT_DIR);
        if (outputDir.isDirectory() && !outputDir.delete()) {
            System.out.println("[ERROR] The output directory already exists and isn't empty. Please empty or delete it");
            return;
        }
        outputDir.mkdirs();

        copyIncludes();
        for (final String schematronFilename : SCHEMATRON_FILES) {
            final File schematronFile = Path.of(SCHEMATRON_INPUT_DIR, schematronFilename + ".sch").toFile();
            optimizeSchematronFile(
                schematronFile,
                Path.of(SCHEMATRON_OUTPUT_DIR, schematronFilename + "-all.xslt").toFile(),
                null);
            optimizeSchematronFile(
                schematronFile,
                Path.of(SCHEMATRON_OUTPUT_DIR, schematronFilename + "-error.xslt").toFile(),
                "error");
        }
        cleanIncludes();
        System.out.println("End of conversion");
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
                                               final String roleToKeep) throws Exception {
        System.out.println("- Transforming " + schematronFile.getName());
        final File optimizedSchematronFile = File.createTempFile("cdachemed_", "_sch");
        optimizedSchematronFile.deleteOnExit();
        if (!schematronFile.isFile() || !schematronFile.canRead()) {
            throw new FileNotFoundException("The Schematron file cannot be found: " + schematronFile);
        }

        System.out.println("  + Optimizing the Schematron definition");
        CdaChEmedSchematronOptimizer.optimizeSchematron(schematronFile, optimizedSchematronFile, roleToKeep);
        System.out.println("  + Converting it to XSLT");
        CdaChEmedSchematronOptimizer.convertToXslt(optimizedSchematronFile, xsltFile);
        optimizedSchematronFile.delete();
        System.out.println("  + Done");
    }

    /**
     *
     * @throws IOException
     */
    private static void copyIncludes() throws IOException {
        System.out.println("- Copying 'includes/' directory");
        final File srcDirectory = Path.of(SCHEMATRON_INPUT_DIR, "include/").toFile();
        final File distDirectory = Path.of(SCHEMATRON_OUTPUT_DIR, "include/").toFile();
        distDirectory.mkdirs();
        for (final File srcFile : Utils.listFiles(srcDirectory)) {
            Files.copy(srcFile.toPath(), Path.of(SCHEMATRON_OUTPUT_DIR, "include/", srcFile.getName()));
        }
    }

    /**
     * Clean the 'include/' directory by deleting all files that are not used by the Schematron.
     */
    private static void cleanIncludes() {
        System.out.println("- Cleaning the 'include/' directory");

        final List<String> xsltContents = Utils.listFiles(new File(SCHEMATRON_OUTPUT_DIR)).stream()
            .filter(file -> file.getName().endsWith(".sch"))
            .map(Utils::readFileToString)
            .collect(Collectors.toList());

        final File dir = new File(SCHEMATRON_OUTPUT_DIR + "include/");
        int nbDeletedFiles = 0;
        for (final File includeFile : Utils.listFiles(dir)) {
            // Only the value set files (starting with "voc-") are still linked
            if (!includeFile.getName().startsWith("voc-")) {
                includeFile.delete();
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
                includeFile.delete();
                ++nbDeletedFiles;
            }
        }
        System.out.println("  + Cleaned " + nbDeletedFiles + " useless includes");
    }
}
