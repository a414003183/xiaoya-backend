package net.zentao.platform.file;

/** PO → View 装配（url = /files/{id}/download，platform 卡 §3.5）。 */
final class FileViews {

  private FileViews() {}

  static FileView toView(FilePO po) {
    return new FileView(po.getId(), po.getTitle(), "/api/v1/files/" + po.getId() + "/download",
        po.getExtension(), po.getSize() == null ? 0 : po.getSize(),
        po.getObjectType() == null ? "" : po.getObjectType(), po.getObjectId() == null ? 0 : po.getObjectId(),
        po.getDownloads() == null ? 0 : po.getDownloads(), po.getCreatedBy(), po.getCreatedAt());
  }
}
