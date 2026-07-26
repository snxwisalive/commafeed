package com.commafeed.frontend.resource;

import com.commafeed.backend.model.User;
import com.commafeed.backend.service.EntryNoteService;
import com.commafeed.frontend.model.EntryNote;
import com.commafeed.frontend.model.request.EntryNoteRequest;
import com.commafeed.security.AuthenticationContext;
import com.commafeed.security.Roles;
import com.google.common.base.Preconditions;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/rest/entry")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Singleton
@Tag(name = "Entry notes")
public class EntryNoteREST {

    private final AuthenticationContext authenticationContext;
    private final EntryNoteService entryNoteService;

    @Path("/note")
    @POST
    @Transactional
    @Operation(summary = "Create or update a note on a feed entry")
    public Response saveNote(@Valid EntryNoteRequest req) {
        Preconditions.checkNotNull(req);
        Preconditions.checkNotNull(req.getEntryId());

        User user = authenticationContext.getCurrentUser();
        com.commafeed.backend.model.EntryNote note =
                entryNoteService.saveNote(
                        user, req.getEntryId(), req.getComment(), req.getStarRating());

        if (note == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(EntryNote.build(note)).build();
    }

    @Path("/notes")
    @GET
    @Transactional
    @Operation(summary = "List all notes for the current user")
    public Response getNotes() {
        User user = authenticationContext.getCurrentUser();
        List<EntryNote> notes =
                entryNoteService.findNotesForUser(user).stream().map(EntryNote::build).toList();
        return Response.ok(notes).build();
    }
}
