package com.commafeed.integration.rest;

import com.commafeed.TestConstants;
import com.commafeed.frontend.model.Entries;
import com.commafeed.frontend.model.Entry;
import com.commafeed.frontend.model.EntryNote;
import com.commafeed.frontend.model.request.EntryNoteRequest;
import com.commafeed.integration.BaseIT;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Arrays;
import java.util.List;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EntryNoteIT extends BaseIT {

    @BeforeEach
    void setup() {
        initialSetup(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
        RestAssured.authentication =
                RestAssured.preemptive()
                        .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
    }

    @AfterEach
    void cleanup() {
        RestAssured.reset();
    }

    @Test
    void saveAndListNotes() {
        long subscriptionId = subscribeAndWaitForEntries(getFeedUrl());
        Entries entries = getFeedEntries(subscriptionId);
        Entry entry = entries.getEntries().getFirst();
        long entryId = Long.parseLong(entry.getId());

        EntryNoteRequest req = new EntryNoteRequest();
        req.setEntryId(entryId);
        req.setComment("Great article on async patterns");
        req.setStarRating(4);

        EntryNote created =
                RestAssured.given()
                        .body(req)
                        .contentType(ContentType.JSON)
                        .post("rest/entry/note")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(EntryNote.class);

        Assertions.assertNotNull(created.getId());
        Assertions.assertEquals(entryId, created.getEntryId());
        Assertions.assertEquals("Great article on async patterns", created.getComment());
        Assertions.assertEquals(4, created.getStarRating());
        Assertions.assertNotNull(created.getUpdated());

        List<EntryNote> notes =
                Arrays.asList(
                        RestAssured.given()
                                .get("rest/entry/notes")
                                .then()
                                .statusCode(HttpStatus.SC_OK)
                                .extract()
                                .as(EntryNote[].class));

        Assertions.assertEquals(1, notes.size());
        Assertions.assertEquals(created.getId(), notes.getFirst().getId());
        Assertions.assertEquals("Great article on async patterns", notes.getFirst().getComment());

        req.setComment("Updated comment");
        req.setStarRating(5);

        EntryNote updated =
                RestAssured.given()
                        .body(req)
                        .contentType(ContentType.JSON)
                        .post("rest/entry/note")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract()
                        .as(EntryNote.class);

        Assertions.assertEquals(created.getId(), updated.getId());
        Assertions.assertEquals("Updated comment", updated.getComment());
        Assertions.assertEquals(5, updated.getStarRating());
    }

    @Test
    void saveNoteForUnsubscribedEntryReturnsNotFound() {
        EntryNoteRequest req = new EntryNoteRequest();
        req.setEntryId(999999L);
        req.setComment("Should not be saved");
        req.setStarRating(3);

        RestAssured.given()
                .body(req)
                .contentType(ContentType.JSON)
                .post("rest/entry/note")
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }
}
