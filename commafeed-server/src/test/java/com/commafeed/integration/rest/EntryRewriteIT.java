package com.commafeed.integration.rest;

import com.commafeed.TestConstants;
import com.commafeed.backend.model.Feed;
import com.commafeed.backend.model.FeedEntry;
import com.commafeed.backend.model.FeedEntryContent;
import com.commafeed.backend.service.LlmRewriteService;
import com.commafeed.frontend.model.request.GenerateAlternativeRequest;
import com.commafeed.integration.BaseIT;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@QuarkusTest
public class EntryRewriteIT extends BaseIT {

    @InjectMock LlmRewriteService llmRewriteServiceMock;

    @Inject EntityManager em;

    private Long testEntryId;

    @BeforeEach
    void setUpAuthAndEntry() {
        initialSetup(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD);
        createTestEntry();
    }

    @Transactional
    void createTestEntry() {
        Feed feed = new Feed();
        feed.setUrl("http://example.com/rss");
        em.persist(feed);

        FeedEntryContent content = new FeedEntryContent();
        content.setTitle("Original Title");
        content.setContent("Original Content");
        em.persist(content);

        FeedEntry entry = new FeedEntry();
        entry.setGuid("test-guid-123");
        entry.setGuidHash("test-hash-123");
        entry.setUrl("http://example.com");
        entry.setContent(content);
        entry.setFeed(feed);
        em.persist(entry);

        testEntryId = entry.getId();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        em.createQuery("DELETE FROM FeedEntry").executeUpdate();
        em.createQuery("DELETE FROM FeedEntryContent").executeUpdate();
        em.createQuery("DELETE FROM Feed").executeUpdate();
    }

    @Test
    void testGenerateAlternativeSuccess() {
        Mockito.when(
                        llmRewriteServiceMock.rewrite(
                                ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn("Rewritten by AI");

        GenerateAlternativeRequest req = new GenerateAlternativeRequest();
        req.setTarget("title");
        req.setPrompt("Make it catchy");

        RestAssured.given()
                .auth()
                .preemptive()
                .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("rest/entry/{id}/generate-alternative", testEntryId)
                .then()
                .statusCode(200)
                .body("originalEntryId", Matchers.equalTo(String.valueOf(testEntryId)))
                .body("target", Matchers.equalTo("title"))
                .body("prompt", Matchers.equalTo("Make it catchy"))
                .body("generatedAlternative", Matchers.equalTo("Rewritten by AI"));
    }

    @Test
    void testEntryNotFound() {
        GenerateAlternativeRequest req = new GenerateAlternativeRequest();
        req.setTarget("title");
        req.setPrompt("Make it catchy");

        RestAssured.given()
                .auth()
                .preemptive()
                .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("rest/entry/{id}/generate-alternative", 999999L)
                .then()
                .statusCode(404);
    }

    @Test
    void testLlmFailureHandledDistinctly() {
        Mockito.when(
                        llmRewriteServiceMock.rewrite(
                                ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenThrow(new WebApplicationException("LLM is down", Response.Status.BAD_GATEWAY));

        GenerateAlternativeRequest req = new GenerateAlternativeRequest();
        req.setTarget("content");
        req.setPrompt("Simplify this");

        RestAssured.given()
                .auth()
                .preemptive()
                .basic(TestConstants.ADMIN_USERNAME, TestConstants.ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(req)
                .when()
                .post("rest/entry/{id}/generate-alternative", testEntryId)
                .then()
                .statusCode(502);
    }
}
