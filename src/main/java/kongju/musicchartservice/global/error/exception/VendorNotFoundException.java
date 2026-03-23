package kongju.musicchartservice.global.error.exception;

import kongju.musicchartservice.global.error.ErrorCode;

public class VendorNotFoundException extends BusinessException {
    public VendorNotFoundException() {
        super(ErrorCode.NOT_FOUND_VENDOR);
    }
}
