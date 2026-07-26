package com.commafeed.backend.service;

import com.commafeed.backend.dao.EntryNoteDAO;
import com.commafeed.backend.dao.FeedEntryDAO;
import com.commafeed.backend.dao.FeedSubscriptionDAO;
import com.commafeed.backend.model.EntryNote;
import com.commafeed.backend.model.FeedEntry;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.User;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Singleton
public class EntryNoteService {

    private final FeedEntryDAO feedEntryDAO;
    private final FeedSubscriptionDAO feedSubscriptionDAO;
    private final EntryNoteDAO entryNoteDAO;

    /**
     * Create or update a note on an entry. Returns null if the entry does not exist or the user is
     * not subscribed.
     */
    public EntryNote saveNote(User user, Long entryId, String comment, int starRating) {
        FeedEntry entry = feedEntryDAO.findById(entryId);
        if (entry == null) {
            return null;
        }

        FeedSubscription sub = feedSubscriptionDAO.findByFeed(user, entry.getFeed());
        if (sub == null) {
            return null;
        }

        EntryNote existing = entryNoteDAO.findByUserAndEntry(user, entry);
        if (existing != null) {
            existing.setComment(comment);
            existing.setStarRating(starRating);
            existing.setUpdated(Instant.now());
            return entryNoteDAO.merge(existing);
        }

        EntryNote note = new EntryNote(user, entry, comment, starRating);
        entryNoteDAO.persist(note);
        return note;
    }

    public List<EntryNote> findNotesForUser(User user) {
        return entryNoteDAO.findByUser(user);
    }
}
