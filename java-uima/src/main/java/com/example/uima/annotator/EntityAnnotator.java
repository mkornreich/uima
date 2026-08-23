package com.example.uima.annotator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import com.example.uima.type.Entity;

/**
 * Regex-based entity recognizer. Finds emails, phones, dates, money, and URLs,
 * writing an {@link Entity} annotation (with a category, a normalized form, and
 * source="java") for each hit.
 */
public class EntityAnnotator extends JCasAnnotator_ImplBase {

    private enum Rule {
        EMAIL("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        PHONE("\\b\\d{3}-\\d{3}-\\d{4}\\b"),
        DATE("\\b\\d{4}-\\d{2}-\\d{2}\\b"),
        MONEY("\\$\\d[\\d,]*(?:\\.\\d+)?"),
        URL("https?://[^\\s]+");

        final Pattern pattern;
        Rule(String regex) { this.pattern = Pattern.compile(regex); }
    }

    @Override
    public void process(JCas jcas) {
        String text = jcas.getDocumentText();
        for (Rule rule : Rule.values()) {
            Matcher m = rule.pattern.matcher(text);
            while (m.find()) {
                int begin = m.start();
                int end = m.end();
                // URLs love to swallow the sentence-ending period; give it back.
                if (rule == Rule.URL) {
                    while (end > begin && ".,;:!?".indexOf(text.charAt(end - 1)) >= 0) end--;
                }
                String surface = text.substring(begin, end);
                Entity e = new Entity(jcas, begin, end);
                e.setCategory(rule.name());
                e.setNormalized(normalize(rule, surface));
                e.setSource("java");
                e.addToIndexes();
            }
        }
    }

    private static String normalize(Rule rule, String surface) {
        switch (rule) {
            case EMAIL: return surface.toLowerCase();
            case PHONE: return surface.replaceAll("\\D", "");
            case MONEY: return surface.replaceAll("[$,]", "");
            default:    return surface;
        }
    }
}
