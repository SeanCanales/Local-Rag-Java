package com.reader.demo;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;

public interface Assistant {
	
	@SystemMessage("""
			You answer questions using only the provided context from the user's documents.
            If the context does not contain the answer, say you could not find it in the documents.
            Keep answers concise and do not invent facts.
			""")Result<String> answer(String question);
}
