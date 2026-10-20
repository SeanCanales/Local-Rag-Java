package com.reader.demo;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RagController {
	public record AskRequest(String question) {}
    public record Source(String file, String excerpt) {}
    public record AskResponse(String answer, List<Source> sources) {}

    private final Assistant assistant;
    private final IngestionService ingestion;

    public RagController(Assistant assistant, IngestionService ingestion) {
        this.assistant = assistant;
        this.ingestion = ingestion;
    }

    @PostMapping("/documents")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        Path tmp = Files.createTempFile("rag-", "-" + name);
        try {
            file.transferTo(tmp);
            ingestion.ingest(tmp, name);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return ResponseEntity.ok("Ingested " + name);
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        Result<String> result = assistant.answer(request.question());
        List<Source> sources = result.sources() == null ? List.of() :
                result.sources().stream().map(RagController::toSource).toList();
        return new AskResponse(result.content(), sources);
    }

    private static Source toSource(Content content) {
        var segment = content.textSegment();
        String file = segment.metadata().getString("file_name");
        String text = segment.text();
        String excerpt = text.length() > 200 ? text.substring(0, 200) + "…" : text;
        return new Source(file == null ? "unknown" : file, excerpt);
    }
}
