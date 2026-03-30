package kongju.musicchartservice.global.error.exception;

import kongju.musicchartservice.global.error.ErrorCode;

public class ScrapingFailedException extends BusinessException {
    public ScrapingFailedException() {
        super(ErrorCode.SCRAPING_FAILED);
    }
}
