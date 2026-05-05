package com.aegispay.common.security;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Holds the authenticated actor's identity for the duration of a request.
 * Stored in a ThreadLocal so any layer can read it without parameter drilling.
 */
public final class ActorContext {

    private static final ThreadLocal<Actor> HOLDER = new ThreadLocal<>();

    public static void set(Actor actor) {
        HOLDER.set(actor);
    }

    public static Actor get() {
        Actor actor = HOLDER.get();
        if (actor == null) {
            throw new IllegalStateException("No actor in context — request not authenticated");
        }
        return actor;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static boolean isPresent() {
        return HOLDER.get() != null;
    }

    @Getter
    @Builder
    public static class Actor {
        private final UUID userId;
        private final String externalId;
        private final String role;
        private final String tenantId;
        private final List<String> authorities;
        private final String correlationId;
        private final String traceParent;
    }
}
