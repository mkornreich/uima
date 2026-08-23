#!/usr/bin/env python3
"""
Python side of the Apache UIMA demo.

It "connects to" the Java UIMA pipeline the way UIMA components are meant to
interoperate: over a serialized CAS. Using dkpro-cassis (a pure-Python UIMA CAS
library) it

  1. loads the TypeSystem.xml + document.xmi that the Java pipeline wrote,
  2. reads the annotations Java produced (sentences, tokens, entities),
  3. adds its own annotations of the SAME shared types (Sentiment per sentence,
     plus KEYWORD entities), and
  4. writes document.enriched.xmi back, which the Java `ReadEnriched` main reloads.

No JVM, no network: just the shared CAS files.
"""
import os
from collections import Counter

from cassis import load_typesystem, load_cas_from_xmi

HERE = os.path.dirname(os.path.abspath(__file__))
SHARED = os.environ.get("SHARED_DIR", os.path.join(HERE, "..", "shared"))

T_TOKEN = "com.example.uima.type.Token"
T_SENTENCE = "com.example.uima.type.Sentence"
T_ENTITY = "com.example.uima.type.Entity"
T_SENTIMENT = "com.example.uima.type.Sentiment"

# Tiny sentiment lexicon — enough to show a real, deterministic computation.
POSITIVE = {"thrilled", "delighted", "excellent", "great", "good", "happy", "success"}
NEGATIVE = {"stressful", "disappointing", "rushed", "bad", "poor", "failed", "sad"}
STOPWORDS = {
    "the", "a", "an", "and", "or", "of", "to", "at", "in", "on", "with", "for",
    "was", "is", "are", "were", "our", "before", "though", "felt", "has", "into",
    "turns", "data", "text",
}


def load_cas():
    with open(os.path.join(SHARED, "TypeSystem.xml"), "rb") as f:
        typesystem = load_typesystem(f)
    with open(os.path.join(SHARED, "document.xmi"), "rb") as f:
        cas = load_cas_from_xmi(f, typesystem=typesystem)
    return typesystem, cas


def score_sentiment(text):
    words = [w.strip(".,;:!?\"'").lower() for w in text.split()]
    pos = sum(w in POSITIVE for w in words)
    neg = sum(w in NEGATIVE for w in words)
    if pos == 0 and neg == 0:
        return "neutral", 0.0
    score = (pos - neg) / (pos + neg)
    label = "positive" if score > 0 else "negative" if score < 0 else "neutral"
    return label, round(score, 3)


def main():
    typesystem, cas = load_cas()
    Sentiment = typesystem.get_type(T_SENTIMENT)
    Entity = typesystem.get_type(T_ENTITY)

    sentences = list(cas.select(T_SENTENCE))
    tokens = list(cas.select(T_TOKEN))
    entities = list(cas.select(T_ENTITY))

    print("=== Python client (dkpro-cassis) ===")
    print(f"loaded document : {len(cas.sofa_string)} chars")
    print(f"read from Java  : {len(sentences)} sentences, {len(tokens)} tokens, "
          f"{len(entities)} entities")
    print()
    print("Entities Java handed us:")
    for e in entities:
        print(f"  [{e.category:<6}] {e.get_covered_text()!r} -> {e.normalized}")
    print()

    # (1) Add a Sentiment annotation over every sentence.
    print("Adding Sentiment annotations (Python):")
    for s in sentences:
        text = s.get_covered_text()
        label, score = score_sentiment(text)
        cas.add(Sentiment(begin=s.begin, end=s.end,
                          polarity=label, score=score, source="python"))
        if label != "neutral":
            print(f"  [{label:<8} {score:+.2f}] {text!r}")

    # (2) Add KEYWORD entities: the most frequent meaningful word tokens.
    freq = Counter()
    spans = {}
    for t in tokens:
        w = t.get_covered_text().lower()
        if t.pos == "WORD" and w.isalpha() and len(w) >= 5 and w not in STOPWORDS:
            freq[w] += 1
            spans.setdefault(w, (t.begin, t.end))
    keywords = [w for w, _ in freq.most_common(5)]
    print()
    print(f"Adding KEYWORD entities (Python): {keywords}")
    for w in keywords:
        begin, end = spans[w]
        cas.add(Entity(begin=begin, end=end, category="KEYWORD",
                       normalized=w, source="python"))

    # (3) Write the enriched CAS back for Java to reload.
    out = os.path.join(SHARED, "document.enriched.xmi")
    cas.to_xmi(out)
    print()
    print(f"Wrote enriched CAS -> {os.path.abspath(out)}")
    print(f"  now contains {len(list(cas.select(T_SENTIMENT)))} Sentiment + "
          f"{len(list(cas.select(T_ENTITY)))} Entity annotations")


if __name__ == "__main__":
    main()
