package net.zentao.platform.file;

import java.time.Instant;

/** FileView（contract：id/title/url/extension/size/objectType/objectId/downloads/createdBy/createdAt）。 */
public record FileView(
    long id,
    String title,
    String url,
    String extension,
    long size,
    String objectType,
    long objectId,
    long downloads,
    String createdBy,
    Instant createdAt) {}
