package net.zentao.platform.web;

/** 单资源信封（03 §2）：{"data": …}；列表用 {@link ListEnvelope}，错误用 {@link ErrorEnvelope}。 */
public record DataEnvelope<T>(T data) {

  public static <T> DataEnvelope<T> of(T data) {
    return new DataEnvelope<>(data);
  }

  public static DataEnvelope<Void> empty() {
    return new DataEnvelope<>(null);
  }
}
