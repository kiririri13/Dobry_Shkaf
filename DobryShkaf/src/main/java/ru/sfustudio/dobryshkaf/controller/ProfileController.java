package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.domain.TransferStatus;
import ru.sfustudio.dobryshkaf.dto.TransferResponse;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.ProfileService;
import ru.sfustudio.dobryshkaf.service.TransferService;

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
