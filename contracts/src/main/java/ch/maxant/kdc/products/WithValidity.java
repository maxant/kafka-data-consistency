package ch.maxant.kdc.products;

import java.time.LocalDateTime;

public interface WithValidity {
    LocalDateTime getFrom();
    LocalDateTime getTo();
}
