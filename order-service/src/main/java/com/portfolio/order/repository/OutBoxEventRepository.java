package com.portfolio.order.repository;

import com.portfolio.order.messaging.outbox.OutBoxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutBoxEventRepository extends JpaRepository<OutBoxEvent, UUID> {
    List<OutBoxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
