package com.commafeed.backend.dao;

import com.commafeed.backend.model.EntryNote;
import com.commafeed.backend.model.FeedEntry;
import com.commafeed.backend.model.QEntryNote;
import com.commafeed.backend.model.User;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import java.util.List;

@Singleton
public class EntryNoteDAO extends GenericDAO<EntryNote> {

    private static final QEntryNote NOTE = QEntryNote.entryNote;

    public EntryNoteDAO(EntityManager entityManager) {
        super(entityManager, EntryNote.class);
    }

    public EntryNote findByUserAndEntry(User user, FeedEntry entry) {
        return query().selectFrom(NOTE).where(NOTE.user.eq(user), NOTE.entry.eq(entry)).fetchOne();
    }

    public List<EntryNote> findByUser(User user) {
        return query().selectFrom(NOTE)
                .where(NOTE.user.eq(user))
                .orderBy(NOTE.updated.desc())
                .fetch();
    }
}
