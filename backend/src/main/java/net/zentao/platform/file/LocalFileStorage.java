package net.zentao.platform.file;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本地磁盘存储（platform 卡 §3.5）：磁盘根 data/files，路径 {yyyyMM}/{uuid}.{ext}。
 *
 * <p>路径一律经 {@link #resolve} 解析（T60 / SEC-17）：normalize 后再校验仍在 root 之内——
 * 纵深防御（当前调用方给的路径都是自己生成的 `{yyyyMM}/{uuid}.{ext}`，但存储层的边界不该依赖调用方自觉）。
 */
@Component
public class LocalFileStorage implements FileStorage {

  private final Path root;

  public LocalFileStorage(@Value("${zentao.file.root:data/files}") String root) {
    // 绝对化：前缀校验要在同一坐标系里比（相对路径的 resolve 结果无处可比）
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Override
  public void store(String relativePath, InputStream content, long size) {
    try {
      Path target = resolve(relativePath);
      Files.createDirectories(target.getParent());
      Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new IllegalStateException("文件写入失败：" + relativePath, e);
    }
  }

  @Override
  public InputStream read(String relativePath) {
    try {
      Path target = resolve(relativePath);
      return Files.exists(target) ? Files.newInputStream(target) : null;
    } catch (Exception e) {
      throw new IllegalStateException("文件读取失败：" + relativePath, e);
    }
  }

  @Override
  public void remove(String relativePath) {
    try {
      Files.deleteIfExists(resolve(relativePath));
    } catch (Exception e) {
      throw new IllegalStateException("文件删除失败：" + relativePath, e);
    }
  }

  /** 存储内的相对路径 → 绝对路径；`..`/绝对路径/软链逃逸出的结果一律拒绝（越界不是"找不到文件"）。 */
  private Path resolve(String relativePath) {
    Path target = root.resolve(relativePath).normalize();
    if (!target.startsWith(root)) {
      throw new IllegalStateException("存储路径越界：" + relativePath);
    }
    return target;
  }
}
