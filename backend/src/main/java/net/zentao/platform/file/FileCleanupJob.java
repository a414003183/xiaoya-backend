package net.zentao.platform.file;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日清理：软删超 30 天的文件删磁盘行并删记录（platform 卡 §3.5）。 */
@Component
public class FileCleanupJob {

  private final FileRepository repository;
  private final FileStorage storage;
  private final int graceDays;

  public FileCleanupJob(FileRepository repository, FileStorage storage,
      @Value("${zentao.file.purge-grace-days:30}") int graceDays) {
    this.repository = repository;
    this.storage = storage;
    this.graceDays = graceDays;
  }

  @Scheduled(cron = "${zentao.file.cleanup-cron:0 30 4 * * *}")
  public void cleanup() {
    Instant threshold = Instant.now().minus(graceDays, ChronoUnit.DAYS);
    for (FilePO po : repository.findPurgeable(threshold)) {
      storage.remove(po.getPath());
      repository.delete(po);
    }
  }
}
