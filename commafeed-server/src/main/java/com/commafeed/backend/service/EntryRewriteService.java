package com.commafeed.backend.service;

import com.commafeed.backend.dao.FeedEntryDAO;
import com.commafeed.backend.model.FeedEntry;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@Singleton
public class EntryRewriteService {

    private final FeedEntryDAO feedEntryDAO;
    private final LlmRewriteService llmRewriteService;

    public EntryRewriteService(FeedEntryDAO feedEntryDAO, LlmRewriteService llmRewriteService) {
        this.feedEntryDAO = feedEntryDAO;
        this.llmRewriteService = llmRewriteService;
    }

    /**
     * Loads the entry, extracts the requested field (title/content), and asks the LLM to rewrite
     * it. Throws WebApplicationException with the correct status for "not found" and "field empty"
     * cases; LLM-layer failures propagate from LlmRewriteService, which already maps them to
     * BAD_GATEWAY / GATEWAY_TIMEOUT.
     */
    public String generateAlternative(Long entryId, String target, String prompt) {
        FeedEntry entry = feedEntryDAO.findById(entryId);
        if (entry == null) {
            throw new WebApplicationException("Feed entry not found", Response.Status.NOT_FOUND);
        }

        String originalText = extractTarget(entry, target);
        if (originalText == null || originalText.isBlank()) {
            throw new WebApplicationException(
                    "The requested target field is empty in this entry",
                    Response.Status.BAD_REQUEST);
        }

        return llmRewriteService.rewrite(originalText, prompt);
    }

    private String extractTarget(FeedEntry entry, String target) {
        if (entry.getContent() == null) {
            return null;
        }
        return switch (target) {
            case "title" -> entry.getContent().getTitle();
            case "content" -> entry.getContent().getContent();
            default -> null;
        };
    }
}
