package net.zentao.platform.file;

import java.io.InputStream;

/** 文件存储抽象（platform 卡 §3.5）：P1 本地磁盘实现，未来对象存储换实现即可。 */
public interface FileStorage {

  /** 写入相对路径文件，返回是否成功。 */
  void store(String relativePath, InputStream content, long size);

  /** 读取相对路径文件；不存在返回 null。 */
  InputStream read(String relativePath);

  /** 删除相对路径文件（磁盘）；不存在静默忽略。 */
  void remove(String relativePath);
}
