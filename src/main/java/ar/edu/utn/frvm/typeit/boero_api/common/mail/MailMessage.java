package ar.edu.utn.frvm.typeit.boero_api.common.mail;

public record MailMessage(
    String from,
    String to,
    String subject,
    String htmlBody,
    MailType type,
    MailAuditContext auditContext) {}
