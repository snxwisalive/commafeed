package com.commafeed.frontend.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@SuppressWarnings("serial")
@Schema(description = "Entry Note Request")
@Data
public class EntryNoteRequest implements Serializable {

    @NotNull
    @Schema(description = "entry id", required = true)
    private Long entryId;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "note comment", required = true)
    private String comment;

    @Min(1)
    @Max(5)
    @Schema(description = "star rating (1-5)", required = true)
    private int starRating;
}
