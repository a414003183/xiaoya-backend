package net.zentao.platform.monitor;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 单机负载快照（T17 P1-5）：直接读 JDK 的 MXBean 与 FileStore，**不依赖 actuator 的暴露面**
 * （actuator 只开了 health/info，为四个数把 metrics 面放开不划算）。
 *
 * <p>哨兵值：拿不到的指标给 -1（CPU 负载在部分平台没有实现），页面据此显示「—」——
 * 用 0 会被读成「空闲」，那是在撒谎。
 */
@Component
public class ServerMetricsQueryService {

  private static final Logger log = LoggerFactory.getLogger(ServerMetricsQueryService.class);
  private static final long UNKNOWN = -1L;

  public record ServerMetricsView(int cpuCores, double cpuLoad, long memoryTotalBytes, long memoryUsedBytes,
      long diskTotalBytes, long diskUsedBytes, String diskPath, long jvmHeapUsedBytes, long jvmHeapMaxBytes,
      long uptimeSeconds, Instant sampledAt) {}

  public ServerMetricsView snapshot() {
    OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    Runtime runtime = Runtime.getRuntime();
    long heapUsed = runtime.totalMemory() - runtime.freeMemory();
    Disk disk = disk();
    return new ServerMetricsView(
        runtime.availableProcessors(),
        os == null ? UNKNOWN : os.getCpuLoad(),
        os == null || os.getTotalMemorySize() <= 0 ? UNKNOWN : os.getTotalMemorySize(),
        os == null || os.getTotalMemorySize() <= 0 ? UNKNOWN : os.getTotalMemorySize() - os.getFreeMemorySize(),
        disk.total(),
        disk.used(),
        disk.path(),
        heapUsed,
        runtime.maxMemory(),
        ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
        Instant.now());
  }

  private record Disk(long total, long used, String path) {}

  /** 进程工作目录所在盘（单机部署下就是数据盘）；读不到给 -1，不让监控页因为磁盘探测失败整页报错。 */
  private static Disk disk() {
    Path path = Path.of("").toAbsolutePath();
    try {
      FileStore store = Files.getFileStore(path);
      long total = store.getTotalSpace();
      return new Disk(total, total - store.getUsableSpace(), path.toString());
    } catch (IOException unreadable) {
      log.warn("disk probe failed path={}", path, unreadable);
      return new Disk(UNKNOWN, UNKNOWN, path.toString());
    }
  }
}
