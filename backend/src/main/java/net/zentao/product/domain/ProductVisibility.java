package net.zentao.product.domain;

/**
 * 产品 ACL 判定（product 卡 §7，纯函数）：
 * public=全部账号；private=createdBy|po|qd|rd|whitelist；custom=仅 whitelist；超管不受限。
 */
public final class ProductVisibility {

  private ProductVisibility() {}

  public static boolean isVisible(Product product, String account, boolean superAdmin) {
    if (superAdmin) {
      return true;
    }
    if (account == null) {
      return false;
    }
    return switch (product.acl()) {
      case "public" -> true;
      case "private" -> account.equals(product.createdBy())
          || account.equals(product.po())
          || account.equals(product.qd())
          || account.equals(product.rd())
          || product.whitelist().contains(account);
      case "custom" -> product.whitelist().contains(account);
      default -> false;
    };
  }
}
