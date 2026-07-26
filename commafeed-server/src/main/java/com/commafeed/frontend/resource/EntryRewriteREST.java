package com.commafeed.frontend.resource;

import com.commafeed.backend.service.EntryRewriteService;
import com.commafeed.frontend.model.GenerateAlternativeResponse;
import com.commafeed.frontend.model.request.GenerateAlternativeRequest;
import com.commafeed.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/rest/entry")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Tag(name = "Feed entries")
public class EntryRewriteREST {

    private final EntryRewriteService entryRewriteService;

    public EntryRewriteREST(EntryRewriteService entryRewriteService) {
        this.entryRewriteService = entryRewriteService;
    }

    @Path("/{id}/generate-alternative")
    @POST
    @Transactional
    @Operation(summary = "Generate an LLM alternative for an entry's title or content")
    public Response generateAlternative(
            @PathParam("id") Long id, @Valid GenerateAlternativeRequest request) {

        String rewrittenText =
                entryRewriteService.generateAlternative(
                        id, request.getTarget(), request.getPrompt());

        GenerateAlternativeResponse response = new GenerateAlternativeResponse();
        response.setOriginalEntryId(String.valueOf(id));
        response.setTarget(request.getTarget());
        response.setPrompt(request.getPrompt());
        response.setGeneratedAlternative(rewrittenText);

        return Response.ok(response).build();
    }
}
