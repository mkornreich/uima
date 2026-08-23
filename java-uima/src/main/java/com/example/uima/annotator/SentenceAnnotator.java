package com.example.uima.annotator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import com.example.uima.type.Sentence;

/**
 * A UIMA Analysis Engine (annotator). Its {@code process} method reads the
 * document text from the CAS and writes {@link Sentence} annotations back into it.
 * Deliberately naive: sentences end at . ! or ?.
 */
public class SentenceAnnotator extends JCasAnnotator_ImplBase {

    private static final Pattern SENTENCE = Pattern.compile("[^.!?]+[.!?]?", Pattern.DOTALL);

    @Override
    public void process(JCas jcas) {
        String text = jcas.getDocumentText();
        Matcher m = SENTENCE.matcher(text);
        while (m.find()) {
            int begin = m.start();
            int end = m.end();
            // Trim surrounding whitespace so the span is tight.
            while (begin < end && Character.isWhitespace(text.charAt(begin))) begin++;
            while (end > begin && Character.isWhitespace(text.charAt(end - 1))) end--;
            if (end <= begin) continue;
            new Sentence(jcas, begin, end).addToIndexes();
        }
    }
}
