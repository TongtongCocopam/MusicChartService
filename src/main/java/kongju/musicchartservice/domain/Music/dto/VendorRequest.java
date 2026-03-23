package kongju.musicchartservice.domain.Music.dto;

import jakarta.validation.constraints.NotNull;

public record VendorRequest(
        @NotNull
        String vendor
) {
}
