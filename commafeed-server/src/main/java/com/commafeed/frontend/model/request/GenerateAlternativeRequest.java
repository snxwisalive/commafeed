package com.commafeed.frontend.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@SuppressWarnings("serial")
@Schema(description = "Request to generate alternative content using LLM")
@Data
public class GenerateAlternativeRequest implements Serializable {

    @NotBlank
    @Pattern(regexp = "^(title|content)$", message = "Target must be either 'title' or 'content'")
    @Schema(description = "Target field to rewrite: 'title' or 'content'", required = true)
    private String target;

    @NotBlank
    @Schema(description = "Free-text instruction for the LLM", required = true)
    private String prompt;
}
