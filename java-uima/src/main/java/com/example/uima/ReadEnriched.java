package com.example.uima;

import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasIOUtils;

import com.example.uima.type.Entity;
import com.example.uima.type.Sentiment;

/**
 * Closes the loop: loads the XMI that the Python client wrote back and prints the
 * annotations Python added (Sentiment + keyword Entities). Proves the CAS round-trips
 * across languages with no loss.
 */
public class ReadEnriched {

    public static void main(String[] args) throws Exception {
        Path shared = Paths.get(System.getProperty("shared.dir", "../shared"))
                .toAbsolutePath().normalize();
        Path enriched = shared.resolve("document.enriched.xmi");
        if (!Files.exists(enriched)) {
            System.err.println("Not found: " + enriched
                    + "\nRun the Python client first (python-client/analyze.py).");
            System.exit(1);
        }

        // Rebuild a CAS from the SAME type system, then load Python's XMI into it.
        TypeSystemDescription tsd = createTypeSystemDescription("com.example.uima.type.ExampleTypeSystem");
        JCas jcas = JCasFactory.createJCas(tsd);
        try (InputStream is = Files.newInputStream(enriched)) {
            CasIOUtils.load(is, jcas.getCas());
        }

        System.out.println("=== Java reading Python's enriched CAS ===");
        System.out.println("Sentiment annotations added by Python:");
        for (Sentiment s : JCasUtil.select(jcas, Sentiment.class)) {
            System.out.printf("  [%-8s %+.2f] %s%n",
                    s.getPolarity(), s.getScore(), snippet(s.getCoveredText()));
        }

        System.out.println();
        System.out.println("Keyword entities added by Python:");
        for (Entity e : JCasUtil.select(jcas, Entity.class)) {
            if ("python".equals(e.getSource())) {
                System.out.printf("  [%-7s] %s%n", e.getCategory(), e.getCoveredText());
            }
        }
    }

    private static String snippet(String s) {
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= 70 ? s : s.substring(0, 67) + "...";
    }
}
