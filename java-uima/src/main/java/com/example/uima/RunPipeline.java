package com.example.uima;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.uima.cas.SerialFormat;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasIOUtils;

import com.example.uima.annotator.EntityAnnotator;
import com.example.uima.annotator.SentenceAnnotator;
import com.example.uima.annotator.TokenAnnotator;
import com.example.uima.type.Entity;
import com.example.uima.type.Sentence;
import com.example.uima.type.Token;

/**
 * Builds a UIMA pipeline with uimaFIT, runs it over a document, prints the
 * annotations, and serializes the CAS + type system to the shared/ folder so the
 * Python client can pick them up. This is the canonical Apache UIMA flow.
 */
public class RunPipeline {

    public static void main(String[] args) throws Exception {
        Path shared = Paths.get(System.getProperty("shared.dir", "../shared"))
                .toAbsolutePath().normalize();
        Files.createDirectories(shared);

        // 1. The type system (schema) that all our annotations conform to.
        TypeSystemDescription tsd = createTypeSystemDescription("com.example.uima.type.ExampleTypeSystem");

        // 2. A CAS wrapped as a JCas: it holds the document text + the annotations.
        JCas jcas = JCasFactory.createJCas(tsd);
        String document = new String(
                RunPipeline.class.getResourceAsStream("/sample.txt").readAllBytes(),
                StandardCharsets.UTF_8);
        jcas.setDocumentText(document);
        jcas.setDocumentLanguage("en");

        // 3. Run the analysis engines in sequence, each adding to the same CAS.
        org.apache.uima.fit.pipeline.SimplePipeline.runPipeline(
                jcas,
                createEngineDescription(SentenceAnnotator.class),
                createEngineDescription(TokenAnnotator.class),
                createEngineDescription(EntityAnnotator.class));

        // 4. Report what UIMA found.
        System.out.println("=== Java UIMA pipeline ===");
        System.out.printf("document length : %d chars%n", document.length());
        System.out.printf("sentences       : %d%n", JCasUtil.select(jcas, Sentence.class).size());
        System.out.printf("tokens          : %d%n", JCasUtil.select(jcas, Token.class).size());
        System.out.println();
        System.out.println("Entities detected by Java:");
        for (Entity e : JCasUtil.select(jcas, Entity.class)) {
            System.out.printf("  [%-6s] %-38s -> %s%n",
                    e.getCategory(), quote(e.getCoveredText()), e.getNormalized());
        }
        System.out.println();
        System.out.println("First sentence's tokens (surface/POS):");
        Sentence first = JCasUtil.select(jcas, Sentence.class).iterator().next();
        StringBuilder sb = new StringBuilder("  ");
        for (Token t : JCasUtil.selectCovered(Token.class, first)) {
            sb.append(t.getCoveredText()).append('/').append(t.getPos()).append("  ");
        }
        System.out.println(sb.toString().stripTrailing());

        // 5. Serialize for the Python client: XMI (the data) + TypeSystem.xml (the schema).
        try (OutputStream os = Files.newOutputStream(shared.resolve("document.xmi"))) {
            CasIOUtils.save(jcas.getCas(), os, SerialFormat.XMI);
        }
        try (OutputStream os = Files.newOutputStream(shared.resolve("TypeSystem.xml"))) {
            tsd.toXML(os);
        }

        System.out.println();
        System.out.println("Wrote CAS + type system to: " + shared);
        System.out.println("  - document.xmi   (the annotated document, XMI format)");
        System.out.println("  - TypeSystem.xml (the shared schema)");
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }
}
