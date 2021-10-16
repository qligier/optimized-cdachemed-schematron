package ch.qligier.emed.ocs;

import lombok.NonNull;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class Utils {

    private static final Logger LOG = Logger.getLogger(Utils.class.getName());

    private Utils() {}

    /**
     * Returns the index in a given list of the first element that satisfies the provided predicate or {@code -1} if the
     * list has no such elements.
     *
     * @param list      The list in which to search.
     * @param predicate The predicate.
     * @param <T>       The object type of the list.
     * @return the index of the satisfying element or {@code -1}.
     */
    public static <T> int listIndexOf(@NonNull final List<T> list,
                                      @NonNull final Predicate<T> predicate) {
        for (int i = 0; i < list.size(); ++i) {
            if (predicate.test(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Initializes and configures a {@link DocumentBuilder} that is not vulnerable to XXE injections (XInclude, Billions
     * Laugh Attack, ...). It's also suitable for OpenSAML processing.
     *
     * @return a configured {@link DocumentBuilder}.
     * @throws ParserConfigurationException if the parser is not Xerces2 compatible.
     * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j">XML
     * External Entity Prevention Cheat Sheet</a>
     * @see <a href="https://wiki.shibboleth.net/confluence/display/OS30/Secure+XML+Processing+Requirements">Secure XML
     * Processing Requirements</a>
     */
    @NonNull
    public static DocumentBuilder newSafeDocumentBuilder() throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://apache.org/xml/features/xinclude", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        return factory.newDocumentBuilder();
    }

    /**
     * Initializes and configures a {@link Transformer}.
     *
     * @return a configured {@link Transformer}.
     * @throws TransformerConfigurationException if it is not possible to create a {@link Transformer} instance.
     */
    @NonNull
    public static Transformer newTransformer() throws TransformerConfigurationException {
        final TransformerFactory transformerFactory = TransformerFactory.newDefaultInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return transformerFactory.newTransformer();
    }

    /**
     *
     *
     * @param directory
     * @return
     */
    @NonNull
    public static List<File> listFiles(@NonNull final File directory) {
        if (!directory.isDirectory()) {
            return Collections.emptyList();
        }
        final File[] files = directory.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        return Stream.of(files)
            .filter(File::isFile)
            .collect(Collectors.toList());
    }

    /**
     *
     * @param file
     * @return
     * @throws FileNotFoundException
     */
    @NonNull
    public static String readFileToString(@NonNull final File file) {
        try (final InputStream inputStream = new FileInputStream(file)) {
            StringBuilder resultStringBuilder = new StringBuilder();
            try (final BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = br.readLine()) != null) {
                    resultStringBuilder.append(line).append("\n");
                }
            }
            return resultStringBuilder.toString();
        } catch (final Exception exception) {
            LOG.log(Level.SEVERE, "Caught error in Utils.readFileToString", exception);
            return "";
        }
    }
}
