package ru.sfustudio.dobryshkaf.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.ItemService;
import ru.sfustudio.dobryshkaf.service.StoredPhoto;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PhotoController {

    private static final Duration CACHE_DURATION = Duration.ofDays(1);

    private final ItemService itemService;

    /** Фото отдаёт сервер, а не хранилище напрямую: так фото черновика видит только владелец. */
    @GetMapping("/api/photos/{photoId}")
    public ResponseEntity<byte[]> get(@PathVariable UUID photoId, Authentication authentication) {
        StoredPhoto photo = itemService.getPhoto(photoId, CurrentUser.from(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_DURATION).cachePrivate())
                .body(photo.content());
    }
}
