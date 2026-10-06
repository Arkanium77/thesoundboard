package app.project;

import java.io.IOException;

/** Keep validation reasons separate from UI wording so background scanning reports one localized explanation. */
public class LinkedAudioFolderException extends IOException {
    public enum Reason { ALREADY_LINKED, INSIDE_PROJECT }
    private final Reason reason;

    public LinkedAudioFolderException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason getReason() { return reason; }
}
