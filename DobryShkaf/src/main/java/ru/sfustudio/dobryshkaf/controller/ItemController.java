package ru.sfustudio.dobryshkaf.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.sfustudio.dobryshkaf.dto.ItemFilter;
import ru.sfustudio.dobryshkaf.dto.ItemRequest;
import ru.sfustudio.dobryshkaf.dto.ItemResponse;
import ru.sfustudio.dobryshkaf.dto.PageResponse;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.ItemService;

import java.util.UUID;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    public PageResponse<ItemResponse> search(@Valid @ModelAttribute ItemFilter filter, Authentication authentication) {
        return itemService.search(filter, CurrentUser.from(authentication));
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable UUID id, Authentication authentication) {
        return itemService.get(id, CurrentUser.from(authentication));
    }

    /** «Отдать вещь»: создаёт черновик, который затем дополняется фото и публикуется. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@Valid @RequestBody ItemRequest request, Authentication authentication) {
        return itemService.create(request, CurrentUser.require(authentication));
    }

    @PutMapping("/{id}")
    public ItemResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request,
            Authentication authentication) {
        return itemService.update(id, request, CurrentUser.require(authentication));
    }

    @PostMapping("/{id}/publish")
    public ItemResponse publish(@PathVariable UUID id, Authentication authentication) {
        return itemService.publish(id, CurrentUser.require(authentication));
    }

    @PostMapping("/{id}/close")
    public ItemResponse close(@PathVariable UUID id, Authentication authentication) {
        return itemService.close(id, CurrentUser.require(authentication));
    }

    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse addPhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return itemService.addPhoto(id, file, CurrentUser.require(authentication));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ItemResponse deletePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId,
            Authentication authentication) {
        return itemService.deletePhoto(id, photoId, CurrentUser.require(authentication));
    }
}
