package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;
import ru.sfustudio.dobryshkaf.dto.TransferResponse;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.ProfileService;
import ru.sfustudio.dobryshkaf.service.TransferService;
import ru.sfustudio.dobryshkaf.service.StoredPhoto;

import java.util.List;
import java.util.UUID;

@Tag(name = "7. Профиль и избранное")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profiles;
    private final TransferService transfers;

    @GetMapping("/profile/me")
    public ProfileService.PrivateProfile me(@Parameter(hidden = true) Authentication authentication) {
        return profiles.me(CurrentUser.require(authentication));
    }

    @PutMapping("/profile/me")
    public ProfileService.PrivateProfile update(@Valid @RequestBody ProfileUpdate request,
                                                 @Parameter(hidden = true) Authentication authentication) {
        return profiles.update(CurrentUser.require(authentication), request.name(), request.districtId());
    }

    @GetMapping("/profiles/{id}")
    public ProfileService.PublicProfile profile(@PathVariable UUID id) {
        return profiles.profile(id);
    }

    @GetMapping("/profiles/{id}/history")
    public ProfileService.PublicHistory publicHistory(@PathVariable UUID id,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page) {
        return profiles.history(id, page);
    }

    @PutMapping(path = "/profile/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileService.PublicProfile setAvatar(@RequestParam("file") MultipartFile file,
                                                    @Parameter(hidden = true) Authentication authentication) {
        return profiles.setAvatar(CurrentUser.require(authentication), file);
    }

    @DeleteMapping("/profile/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@Parameter(hidden = true) Authentication authentication) {
        profiles.removeAvatar(CurrentUser.require(authentication));
    }

    @GetMapping("/profiles/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable UUID id) {
        StoredPhoto photo = profiles.avatar(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(photo.contentType())).body(photo.content());
    }

    @GetMapping("/profile/me/history")
    public List<TransferResponse> history(@Parameter(hidden = true) Authentication authentication) {
        return transfers.list(null, TransferStatus.COMPLETED, CurrentUser.require(authentication));
    }

    @GetMapping("/profile/me/favorites")
    public ProfileService.Favorites favorites(@Parameter(hidden = true) Authentication authentication) {
        return profiles.favorites(CurrentUser.require(authentication));
    }

    @PutMapping("/profile/me/favorites/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable ProfileService.FavoriteType type, @PathVariable UUID id,
                    @Parameter(hidden = true) Authentication authentication) {
        profiles.addFavorite(type, id, CurrentUser.require(authentication));
    }

    @DeleteMapping("/profile/me/favorites/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable ProfileService.FavoriteType type, @PathVariable UUID id,
                       @Parameter(hidden = true) Authentication authentication) {
        profiles.removeFavorite(type, id, CurrentUser.require(authentication));
    }

    public record ProfileUpdate(@NotBlank @Size(max = 100) String name, UUID districtId) {
    }
}
