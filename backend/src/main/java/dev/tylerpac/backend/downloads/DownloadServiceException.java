package dev.tylerpac.backend.downloads;

// The shared download service refused a request, or could not be reached. code is its error code
// (for example bundle_not_ready), passed on so the admin page can show what went wrong.
public class DownloadServiceException extends RuntimeException {

    private final int status;

    public DownloadServiceException(int status, String code) {
        super(code, null, false, false);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public String code() {
        return getMessage();
    }
}
