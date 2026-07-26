package com.commafeed.frontend.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.Serializable;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@SuppressWarnings("serial")
@Schema(description = "LLM Generation Response")
@Data
@RegisterForReflection
public class GenerateAlternativeResponse implements Serializable {

    @Schema(description = "Original entry ID", required = true)
    private String originalEntryId;

    @Schema(description = "The target field that was modified", required = true)
    private String target;

    @Schema(description = "The prompt used for generation", required = true)
    private String prompt;

    @Schema(description = "The generated alternative text", required = true)
    private String generatedAlternative;
}
