package ru.sfustudio.dobryshkaf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ChatAttachment(
        @Column(name = "attachment_key", length = 200, updatable = false) String key,
        @Column(name = "attachment_name", length = 255, updatable = false) String name,
        @Column(name = "attachment_size_bytes", updatable = false) Integer sizeBytes
) {
}
