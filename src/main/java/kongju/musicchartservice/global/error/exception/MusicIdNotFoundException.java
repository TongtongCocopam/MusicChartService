package kongju.musicchartservice.global.error.exception;

import kongju.musicchartservice.global.error.ErrorCode;

public class MusicIdNotFoundException extends BusinessException {
    public MusicIdNotFoundException() {
        super(ErrorCode.NOT_FOUND_MUSIC_ID);
    }
}
