package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.Serializable;
import java.time.Instant;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@SuppressWarnings("serial")
@Schema(description = "Entry Note")
@Data
@RegisterForReflection
public class EntryNote implements Serializable {

    @Schema(description = "note id", required = true)
    private Long id;

    @Schema(description = "entry id", required = true)
    private Long entryId;

    @Schema(description = "note comment", required = true)
    private String comment;

    @Schema(description = "star rating (1-5)", required = true)
    private int starRating;

    @Schema(description = "last update timestamp", required = true)
    private Instant updated;

    public static EntryNote build(com.commafeed.backend.model.EntryNote note) {
        EntryNote dto = new EntryNote();
        dto.setId(note.getId());
        dto.setEntryId(note.getEntry().getId());
        dto.setComment(note.getComment());
        dto.setStarRating(note.getStarRating());
        dto.setUpdated(note.getUpdated());
        return dto;
    }
}
