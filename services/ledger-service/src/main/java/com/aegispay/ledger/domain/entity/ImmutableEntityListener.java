package com.aegispay.ledger.domain.entity;

import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

public class ImmutableEntityListener {

    @PreUpdate
    public void onPreUpdate(Object entity) {
        throw new UnsupportedOperationException(
                "Ledger entries are immutable and cannot be updated: " + entity.getClass().getSimpleName());
    }

    @PreRemove
    public void onPreRemove(Object entity) {
        throw new UnsupportedOperationException(
                "Ledger entries are immutable and cannot be deleted: " + entity.getClass().getSimpleName());
    }
}
