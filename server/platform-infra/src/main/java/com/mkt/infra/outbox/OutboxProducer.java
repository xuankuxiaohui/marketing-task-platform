package com.mkt.infra.outbox;

/** Application id written to {@code sys_outbox.producer} (D-11). */
public enum OutboxProducer {
    ADMIN("admin"),
    PORTAL("portal");

    private final String id;

    OutboxProducer(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static OutboxProducer require(String id) {
        if ("admin".equals(id)) {
            return ADMIN;
        }
        if ("portal".equals(id)) {
            return PORTAL;
        }
        throw new IllegalArgumentException("outbox producer must be admin|portal, got: " + id);
    }
}
