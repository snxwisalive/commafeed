package com.commafeed.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ENTRYNOTES")
@SuppressWarnings("serial")
@Getter
@Setter
public class EntryNote extends AbstractModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private FeedEntry entry;

    @Column(name = "comment", length = 500, nullable = false)
    private String comment;

    @Column(name = "star_rating", nullable = false)
    private int starRating;

    @Column(name = "updated", nullable = false)
    private Instant updated;

    public EntryNote() {}

    public EntryNote(User user, FeedEntry entry, String comment, int starRating) {
        this.user = user;
        this.entry = entry;
        this.comment = comment;
        this.starRating = starRating;
        this.updated = Instant.now();
    }
}
