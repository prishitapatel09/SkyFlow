package com.skyflow.notification.service;

import java.util.List;

/**
 * A rendered email, ready to send.
 *
 * @param details label/value rows shown as a table in the HTML body and as lines in the plain-text
 *                alternative
 */
public record EmailContent(String subject, String heading, String intro, List<Detail> details,
                           String footer) {

    public record Detail(String label, String value) {
    }

    /** Plain-text alternative, for clients that will not render HTML. */
    public String toPlainText() {
        StringBuilder text = new StringBuilder(heading).append("\n\n").append(intro).append("\n\n");
        for (Detail detail : details) {
            text.append(detail.label()).append(": ").append(detail.value()).append('\n');
        }
        if (footer != null) {
            text.append('\n').append(footer).append('\n');
        }
        return text.toString();
    }
}
