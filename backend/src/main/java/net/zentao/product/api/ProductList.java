package net.zentao.product.api;

import java.util.List;

/** 产品分页载荷（contract：ProductList = items + total）。 */
public record ProductList(List<ProductView> items, long total) {}
