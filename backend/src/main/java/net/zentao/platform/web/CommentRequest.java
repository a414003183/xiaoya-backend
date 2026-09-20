package net.zentao.platform.web;

/** 仅带可选备注的动作请求体（contract：CommentRequest；close/activate/finish/terminate 等共用）。 */
public record CommentRequest(String comment) {}
