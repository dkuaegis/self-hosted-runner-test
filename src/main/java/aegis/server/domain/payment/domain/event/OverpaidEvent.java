package aegis.server.domain.payment.domain.event;

import aegis.server.domain.payment.dto.internal.TransactionInfo;

public record OverpaidEvent(TransactionInfo transactionInfo) {}
