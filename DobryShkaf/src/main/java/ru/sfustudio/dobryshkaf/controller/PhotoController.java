package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.UUID;

@Tag(name = "2. Объявления")
@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final ItemService itemService;

    /** Фото отдаёт сервер, а не хранилище напрямую: так фото черновика видит только владелец. */
    @Operation(summary = "Получить фото", description = "Ссылка на фото есть в поле photos[].url объявления")
    @GetMapping(path = "/api/photos/{photoId}", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"})
    public ResponseEntity<byte[]> get(@PathVariable UUID photoId, @Parameter(hidden = true) Authentication authentication) {
        StoredPhoto photo = itemService.getPhoto(photoId, CurrentUser.from(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(photo.content());
    }
}
