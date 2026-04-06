package kongju.musicchartservice.global.error.exception;

import kongju.musicchartservice.global.error.ErrorCode;

public class ScrapingLockAcquisitionException extends BusinessException {
    public ScrapingLockAcquisitionException() {
        super(ErrorCode.SCRAPING_LOCKED);
    }
}
