# Apache UIMA — cross-language demo (Java pipeline + Python client)

## What is Apache UIMA?

**UIMA = Unstructured Information Management Architecture.** It's an Apache
framework (and an OASIS standard) for building pipelines that turn *unstructured*
content — text, audio, video — into *structured* data by attaching **annotations**
to it. It's the plumbing behind systems like IBM Watson and many clinical/NLP
platforms (Apache cTAKES, DKPro, ClearTK are built on it).

You don't write one giant program. You write small, reusable **analysis engines**
(annotators) — a tokenizer, a sentence splitter, a named-entity recognizer — and
UIMA runs them in sequence over a shared document, each one adding to what the
previous one found.

### The five core ideas (and where to see each in this repo)

| UIMA concept | What it is | In this demo |
|---|---|---|
| **CAS** (Common Analysis System) | The in-memory object holding the document text *plus* all annotations. The thing that flows through the pipeline. | `shared/document.xmi` |
| **Type System** | The schema: which annotation types exist and what features (fields) they have. | [`ExampleTypeSystem.xml`](java-uima/src/main/resources/com/example/uima/type/ExampleTypeSystem.xml) → `shared/TypeSystem.xml` |
| **Annotation** | A typed, feature-bearing label over a `[begin, end)` span of text. | `Token`, `Sentence`, `Entity`, `Sentiment` |
| **Analysis Engine** (annotator) | A component whose `process(CAS)` reads the CAS and adds annotations. | [`SentenceAnnotator`](java-uima/src/main/java/com/example/uima/annotator/SentenceAnnotator.java), [`TokenAnnotator`](java-uima/src/main/java/com/example/uima/annotator/TokenAnnotator.java), [`EntityAnnotator`](java-uima/src/main/java/com/example/uima/annotator/EntityAnnotator.java) |
| **XMI serialization** | The standard XML wire format for a CAS. This is how UIMA components — even in different languages — exchange work. | `CasIOUtils.save(...)` / cassis `to_xmi()` |

## What this demo does

```
   Java  (Apache UIMA 3.6.1 + uimaFIT)                 Python (dkpro-cassis)
 ┌───────────────────────────────────┐              ┌──────────────────────────┐
 │ RunPipeline                       │  TypeSystem.xml │ analyze.py              │
 │  • Sentence / Token / Entity      │ ───────────► │  • reads Java's           │
 │    annotators run over sample.txt │  document.xmi │    annotations            │
 │  • serialize CAS to XMI           │              │  • adds Sentiment (per    │
 └───────────────────────────────────┘              │    sentence) + KEYWORD    │
                                                     │    entities               │
 ┌───────────────────────────────────┐  document.   │  • writes CAS back        │
 │ ReadEnriched                      │  enriched.xmi └──────────────────────────┘
 │  • reloads the CAS Python wrote   │ ◄──────────────────────┘
 │  • prints Python's annotations    │
 └───────────────────────────────────┘
```

The Java and Python programs never call each other directly. They **"connect"**
the way real UIMA components do: by passing a **serialized CAS** (an `.xmi` file)
plus the shared **type system** through the `shared/` folder. Because both sides
agree on the type system, Python can read Java's `Entity` annotations and add its
own `Sentiment` annotations that Java then reads back — with no loss.

## Run it

```bash
./demo.sh
```

That compiles the Java project, runs the pipeline, runs the Python client in a
virtualenv (created on first run), and re-reads the enriched CAS in Java.

### Run the pieces by hand

```bash
# 1) Java pipeline: annotate + write shared/document.xmi and shared/TypeSystem.xml
cd java-uima
mvn -q compile
mvn -q exec:java -Dexec.mainClass=com.example.uima.RunPipeline
```

```bash
# 2) Python client: read that CAS, enrich it, write shared/document.enriched.xmi
cd python-client
python3 -m venv .venv && ./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python analyze.py
```

```bash
# 3) Java: reload what Python added
cd java-uima
mvn -q exec:java -Dexec.mainClass=com.example.uima.ReadEnriched
```

## Project layout

```
uima/
├── demo.sh                     # runs the whole Java → Python → Java loop
├── shared/                     # CAS interchange files (created at runtime)
│   ├── TypeSystem.xml          #   the shared schema
│   ├── document.xmi            #   Java's output
│   └── document.enriched.xmi   #   Python's output
├── java-uima/                  # Maven project — the Apache UIMA pipeline
│   ├── pom.xml
│   └── src/main/
│       ├── resources/com/example/uima/type/ExampleTypeSystem.xml
│       ├── resources/sample.txt
│       └── java/com/example/uima/
│           ├── RunPipeline.java       # build + run the pipeline, serialize the CAS
│           ├── ReadEnriched.java      # reload Python's enriched CAS
│           └── annotator/             # the analysis engines
└── python-client/              # the Python side
    ├── requirements.txt        # dkpro-cassis
    └── analyze.py
```

## Notes

- **uimaFIT** is used on the Java side so the pipeline is defined with plain
  annotated classes instead of XML descriptor files — the modern way to write UIMA.
- **JCasGen** (the `jcasgen-maven-plugin`) generates strongly-typed Java classes
  (`Token`, `Sentence`, ...) from the type system at build time.
- **dkpro-cassis** lets Python read/write the exact same CAS format with no JVM.
- The sentence splitter, tokenizer, and entity rules are intentionally simple
  (regex-based) to keep the focus on the UIMA mechanics. You'll notice the naive
  splitter breaks on `Dr.` and email dots — that's real-world tokenization for you.

## Requirements

- Java 17+ (built and tested on JDK 25) and Maven
- Python 3.9+ (tested on 3.14)
