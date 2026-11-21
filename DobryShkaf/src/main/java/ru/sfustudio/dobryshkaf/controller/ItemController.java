package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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

@Tag(name = "2. Объявления", description = "«Отдать вещь»: создать черновик → загрузить 1–5 фото → опубликовать")
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @Operation(summary = "Каталог: поиск и фильтры", description = "По 20 записей, от новых к старым. mine=true — свои объявления, включая черновики")
    @GetMapping
    public PageResponse<ItemResponse> search(
            @Valid @ParameterObject @ModelAttribute ItemFilter filter,
            @Parameter(hidden = true) Authentication authentication) {
        return itemService.search(filter, CurrentUser.from(authentication));
    }

    @Operation(summary = "Объявление по id", description = "Черновик и закрытое объявление видит только владелец")
    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return itemService.get(id, CurrentUser.from(authentication));
    }

    /** «Отдать вещь»: создаёт черновик, который затем дополняется фото и публикуется. */
    @Operation(summary = "Отдать вещь: создать черновик", description = "Для черновика достаточно названия")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(
            @Valid @RequestBody ItemRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        return itemService.create(request, CurrentUser.require(authentication));
    }

    @Operation(summary = "Изменить объявление", description = "Только владелец или администратор")
    @PutMapping("/{id}")
    public ItemResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request,
            @Parameter(hidden = true) Authentication authentication) {
        return itemService.update(id, request, CurrentUser.require(authentication));
    }

    @Operation(summary = "Опубликовать черновик", description = "Нужны название, описание, состояние, категория, район, фото и размер, если его требует категория")
    @PostMapping("/{id}/publish")
    public ItemResponse publish(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return itemService.publish(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Закрыть объявление")
    @PostMapping("/{id}/close")
    public ItemResponse close(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return itemService.close(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Открыть закрытое объявление и отменить его экологический вклад")
    @PostMapping("/{id}/reopen")
    public ItemResponse reopen(@PathVariable UUID id, @Parameter(hidden = true) Authentication authentication) {
        return itemService.reopen(id, CurrentUser.require(authentication));
    }

    @Operation(summary = "Загрузить фото", description = "JPEG, PNG или WebP до 5 МБ, не больше 5 фото на объявление")
    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse addPhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) Authentication authentication) {
        return itemService.addPhoto(id, file, CurrentUser.require(authentication));
    }

    @Operation(summary = "Удалить фото")
    @DeleteMapping("/{id}/photos/{photoId}")
    public ItemResponse deletePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId,
            @Parameter(hidden = true) Authentication authentication) {
        return itemService.deletePhoto(id, photoId, CurrentUser.require(authentication));
    }
}
