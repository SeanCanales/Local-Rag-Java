# Local RAG: ask questions about your documents, fully offline

A document Q&A application in Java. Upload PDF, text or Markdown files, ask questions in plain English, and get an answer together with the passages it was based on. Everything runs on your own machine: no external APIs and no data leaving the computer.

**Stack:** Java, Spring Boot, LangChain4j, Ollama (`llama3.2:3b` for answers, `nomic-embed-text` for embeddings), PostgreSQL with pgvector, Docker Compose.

[Screenshot or demo GIF here]

## How it works

```
Upload   file -> parse (PDFBox / text) -> split into chunks -> embed -> store in pgvector

Question -> embed -> similarity search -> keep top-k chunks above a score threshold
         -> prompt = question + retrieved chunks -> local LLM -> answer + cited sources
```

If no chunk scores above the threshold, nothing is injected and the model is instructed to say it could not find the answer in the documents.

## Quick start

Requirements: Docker Desktop, [Ollama](https://ollama.com), and the Java version set in `pom.xml` (currently 25).

```bash
# 1. Models (once)
ollama pull llama3.2:3b
ollama pull nomic-embed-text

# 2. Vector database
docker compose up -d

# 3. Run the app
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Open http://localhost:8080, add a document, and ask a question. The API can also be called directly:

```bash
curl -F "file=@document.pdf" http://localhost:8080/api/documents

curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What does the document say about X?"}'
```

## Configuration

| Setting | Value | Where |
|---|---|---|
| Chunk size / overlap | 500 / 100 characters | `IngestionService` |
| Chunks retrieved (top-k) | 6 | `rag.max-results` |
| Minimum similarity score | 0.80 | `rag.min-score` |
| Chat model | `llama3.2:3b` | `ollama.chat-model` |
| Embedding model | `nomic-embed-text` (768 dimensions) | `ollama.embedding-model` |

These values come from the evaluation below, not from defaults.

## Evaluation

Retrieval quality decides whether any answer can be right, so I measured it instead of guessing the settings. The evaluation harness (`EvalRunner`) runs the retrieval step against a fixed question set and scores it. It never calls the chat model, so scoring is a plain text match with no AI judge.

### Test set

| Document | Pages | Why it is in the set |
|---|---|---|
| Dublin City Council Annual Report and Accounts 2025 | 108 | Large, fact-dense narrative with statistics |
| Instagram Terms of Use (effective 19 Jan 2013) | 7 | US-style legal text |
| TikTok Terms of Service (EEA, UK and Switzerland version, updated July 2026, saved 20 Oct 2026) | 16 | Overlaps with the Instagram terms on purpose (age limits, content licences, termination), so retrieval has to pick the right company |

The documents are not included in this repository because they belong to their publishers. Download links: [add links].

**25 questions:** 17 single-fact, 3 that need two separate passages, and 5 unanswerable (off-topic, so the correct behaviour is "not found"). The question set was drafted with AI assistance and checked against the source documents. Each question lists a short exact phrase that must appear in the retrieved text, and the harness rejects any question whose phrase is not present in its source file. See `eval/questions.json`.

### Method

- **Hit rate:** the share of the 20 answerable questions where every expected phrase appears in the retrieved chunks.
- **MRR:** how high the first sufficient chunk ranks (1.0 means always first).
- **Correct "not found":** the share of the 5 unanswerable questions where nothing scores above the threshold.
- **Grid:** 3 chunk sizes (300, 500, 800) x 3 overlaps (0, 50, 100) x 4 values of top-k (3, 4, 6, 8) x 5 score thresholds (0.70 to 0.90), 180 configurations in all. The documents are re-ingested into a separate table for each chunking setting.

Run it yourself:

```bash
# put the PDFs in eval/documents/, then run the app with the eval profile
--spring.profiles.active=eval
```

Full results are written to `eval/results.csv`.

### Results

| Configuration | Hit rate | MRR | Correct "not found" |
|---|---|---|---|
| **Baseline:** chunk 500, overlap 50, top-4, min 0.50 | 70% | 0.575 | 0% |
| chunk 500, overlap 100, top-6, min 0.70 | 90% | 0.564 | 0% |
| chunk 500, overlap 100, top-6, min 0.75 | 90% | 0.564 | 60% |
| **Chosen:** chunk 500, overlap 100, top-6, min 0.80 | **90%** | 0.564 | **100%** |
| chunk 500, overlap 100, top-6, min 0.85 | 90% | 0.564 | 100% |

### What the results showed

1. **Retrieving more context, with more overlap, produced the hit-rate gain** (70% to 90%). Ranking did not improve: MRR stayed flat, so the right chunk is found, but further down the list.
2. **A similarity threshold was needed to detect unanswerable questions, and the obvious values did nothing.** With `nomic-embed-text` almost every chunk scores between 0.70 and 0.95. Off-topic questions topped out around 0.73 to 0.78 while real answers scored 0.87 or more, so thresholds of 0.30 to 0.70 filtered nothing (0% correct "not found"). A threshold of 0.80 removed all off-topic matches without losing any hits.
3. **A smaller configuration is a reasonable alternative.** Chunk 800, overlap 50, top-4 reached 85% hit rate with a better MRR (0.638) and about 40% fewer chunks (313 against 536).

### Known failures

- **TikTok: reclaiming an inactive account name (q02).** The sentence sits in a short section that mixes several unrelated clauses, so its chunk does not resemble the question.
- **Council report: 3,594 gully cleaning requests (q09).** The figure is a stand-alone infographic line with no surrounding sentence, so its chunk is about something else. Statistics in call-out boxes are a weakness of this approach.

### Limitations

- **The set is small** (20 answerable questions, so each one is worth 5 points), and the settings were chosen on the same questions they were measured on. Treat 90% as optimistic for unseen questions and small differences between configurations as tentative.
- **The unanswerable questions are easy.** They are completely off-topic. Near-miss questions (for example "What is TikTok's refund policy?") would score higher and are likely to get through the threshold.
- **Thresholds are specific to this embedding model**, whose scores are compressed into a narrow band.
- **Only retrieval is measured.** Answer quality from the chat model has not been evaluated systematically.
- **Scanned PDFs are not supported.** Text is extracted with PDFBox, so a file without a text layer indexes as empty.

## Project structure

```
src/main/java/com/reader/demo/
  AiConfig.java            Ollama models, pgvector store, retriever, assistant
  Assistant.java           LangChain4j AI service and system prompt
  IngestionService.java    parse -> chunk -> embed -> store
  RagController.java       POST /api/documents, POST /api/ask
  EvalRunner.java          retrieval evaluation harness (eval profile only)
  EvalQuestion.java        question format for the harness
src/main/resources/static/index.html    web UI
eval/questions.json                     evaluation questions
docker-compose.yml                      PostgreSQL with pgvector
```

## Roadmap

- [ ] Near-miss unanswerable questions in the evaluation set
- [ ] Streaming answers in the UI
- [ ] List and delete stored documents, and skip duplicate uploads
- [ ] Integration tests with Testcontainers
- [ ] Dockerfile and a single-command full stack
- [ ] OCR for scanned PDFs
