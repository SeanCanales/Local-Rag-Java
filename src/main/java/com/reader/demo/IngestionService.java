package com.reader.demo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class IngestionService {
	private static final int CHUNK_SIZE = 500;
	private static final int CHUNK_OVERLAP = 50;
	
	private final EmbeddingStoreIngestor ingestor;
	
	public IngestionService(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
    }

    /** Parses, chunks, embeds and stores one file. */
    public void ingest(Path file, String originalName) {
        DocumentParser parser = originalName.toLowerCase().endsWith(".pdf")
                ? new ApachePdfBoxDocumentParser()
                : new TextDocumentParser();

        Document document = FileSystemDocumentLoader.loadDocument(file, parser);
        // Keep the real file name so answers can cite it (temp file names are meaningless)
        document.metadata().put("file_name", originalName);
        ingestor.ingest(document);
    }
}
