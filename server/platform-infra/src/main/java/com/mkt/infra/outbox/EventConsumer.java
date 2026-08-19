package com.mkt.infra.outbox;

public interface EventConsumer {

    ConsumerDirection direction();

    void consume(OutboxRecord row);
}
