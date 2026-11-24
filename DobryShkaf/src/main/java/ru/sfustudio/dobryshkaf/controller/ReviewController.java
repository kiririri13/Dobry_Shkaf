package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.ReviewService;

import java.util.List;
import java.util.UUID;

@Tag(name = "8. Отзывы")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviews;

    @PostMapping("/api/transfers/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewService.Review create(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request,
                                       @Parameter(hidden = true) Authentication authentication) {
        return reviews.create(id, CurrentUser.require(authentication), request.rating(), request.text());
    }

    @GetMapping("/api/profiles/{id}/reviews")
    public List<ReviewService.PublicReview> list(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return reviews.list(id, page);
    }

    @GetMapping("/api/profile/me/reviews")
    public List<ReviewService.ReceivedReview> received(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                      @Parameter(hidden = true) Authentication authentication) {
        return reviews.received(CurrentUser.require(authentication), page);
    }

    public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String text) {
    }
}
