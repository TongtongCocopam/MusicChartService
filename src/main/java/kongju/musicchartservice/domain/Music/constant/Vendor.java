package kongju.musicchartservice.domain.Music.constant;

public enum Vendor {
    GENIE, MELON, VENDOR;

    public static Vendor fromString(String value) {
        return Vendor.valueOf(value.toUpperCase());
    }
}
