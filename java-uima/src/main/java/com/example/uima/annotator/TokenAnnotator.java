package com.example.uima.annotator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import com.example.uima.type.Token;

/**
 * Tokenizes the document and tags each token with a coarse part-of-speech guess.
 * Shows how one annotator's output ({@link Token}) enriches the same CAS the
 * {@link SentenceAnnotator} already wrote to.
 */
public class TokenAnnotator extends JCasAnnotator_ImplBase {

    // A "word" (letters/digits, allowing internal ' . -) OR a single punctuation char.
    private static final Pattern TOKEN =
            Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*|[\\p{Punct}]");

    @Override
    public void process(JCas jcas) {
        String text = jcas.getDocumentText();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String surface = m.group();
            Token token = new Token(jcas, m.start(), m.end());
            token.setPos(posOf(surface));
            token.addToIndexes();
        }
    }

    private static String posOf(String s) {
        if (s.chars().allMatch(Character::isDigit)) return "NUM";
        if (s.length() == 1 && !Character.isLetterOrDigit(s.charAt(0))) return "PUNCT";
        if (Character.isUpperCase(s.charAt(0))) return "PROPN";
        return "WORD";
    }
}
