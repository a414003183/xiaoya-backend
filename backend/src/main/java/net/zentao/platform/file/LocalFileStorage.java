package net.zentao.platform.file;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 本地磁盘存储（platform 卡 §3.5）：磁盘根 data/files，路径 {yyyyMM}/{uuid}.{ext}。 */
@Component
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(@Value("${zentao.file.root:data/files}") String root) {
    this.root = Path.of(root);
  }

  @Override
  public void store(String relativePath, InputStream content, long size) {
    try {
      Path target = root.resolve(relativePath);
      Files.createDirectories(target.getParent());
      Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new IllegalStateException("文件写入失败：" + relativePath, e);
    }
  }

  @Override
  public InputStream read(String relativePath) {
    try {
      Path target = root.resolve(relativePath);
      return Files.exists(target) ? Files.newInputStream(target) : null;
    } catch (Exception e) {
      throw new IllegalStateException("文件读取失败：" + relativePath, e);
    }
  }

  @Override
  public void remove(String relativePath) {
    try {
      Files.deleteIfExists(root.resolve(relativePath));
    } catch (Exception e) {
      throw new IllegalStateException("文件删除失败：" + relativePath, e);
    }
  }
}
