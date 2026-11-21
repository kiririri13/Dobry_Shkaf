package ru.sfustudio.dobryshkaf.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.sfustudio.dobryshkaf.service.CurrentUser;
import ru.sfustudio.dobryshkaf.service.EcoService;
import ru.sfustudio.dobryshkaf.service.EcoService.LineInput;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "11. Статистика и экологический вклад")
@RestController
@RequiredArgsConstructor
public class EcoController {

    private final EcoService eco;

    @GetMapping("/api/stats")
    public EcoService.Stats stats() {
        return eco.stats(null);
    }

    @GetMapping("/api/profile/me/stats")
    public EcoService.Stats myStats(@Parameter(hidden = true) Authentication auth) {
        return eco.stats(CurrentUser.require(auth).id());
    }

    @GetMapping("/api/eco/methodology")
    public EcoService.Methodology methodology() {
        return eco.methodology();
    }

    @GetMapping("/api/items/{id}/eco")
    public EcoService.ItemEco item(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        return eco.details(id, CurrentUser.require(auth));
    }

    @PutMapping("/api/items/{id}/eco")
    public List<EcoService.Line> replace(@PathVariable UUID id, @Valid @RequestBody Composition request,
                                        @Parameter(hidden = true) Authentication auth) {
        return eco.replaceLines(id, request.lines(), CurrentUser.require(auth));
    }

    @GetMapping("/api/transfers/{id}/eco-lines")
    public List<EcoService.Line> receivedLines(@PathVariable UUID id, @Parameter(hidden = true) Authentication auth) {
        return eco.transferLines(id, CurrentUser.require(auth));
    }

    @PutMapping("/api/admin/eco/point-batches/{id}")
    public EcoService.PointBatch batch(@PathVariable UUID id, @Valid @RequestBody Batch request,
                                       @Parameter(hidden = true) Authentication auth) {
        return eco.recordBatch(id, request.pointId(), request.kind(), request.quantity(), request.weightKg(),
                CurrentUser.require(auth));
    }

    @PostMapping("/api/admin/eco/items/{id}/recalculate")
    public List<EcoService.Recalculation> recalculate(@PathVariable UUID id,
                                                     @Valid @RequestBody AdminModerationController.Reason request,
                                                     @Parameter(hidden = true) Authentication auth) {
        return eco.recalculate(id, request.reason().strip(), CurrentUser.require(auth));
    }

    public record Composition(@NotNull @Size(max = 50) List<@NotNull @Valid LineInput> lines) {
    }

    public record Batch(@NotNull UUID pointId, @NotNull EcoService.BatchKind kind, @NotNull @Min(1) Integer quantity,
                        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal weightKg) {
    }
}
