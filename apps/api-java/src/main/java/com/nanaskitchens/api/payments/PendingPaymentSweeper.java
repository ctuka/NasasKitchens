package com.nanaskitchens.api.payments;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Story 3.4 — abandoned checkouts (payment sheet opened, never paid) would hold portions
 * hostage forever: no webhook ever fires for "the buyer closed the tab". This sweeper
 * cancels the intent FIRST (so a late payment can't be captured), and only on success
 * releases the order; if the intent turned out to be terminal, the webhook owns it.
 * Mock-mode orders never stay pending, so this is a no-op there.
 */
@Component
public class PendingPaymentSweeper {

    private final JdbcClient db;
    private final PaymentsService payments;
    private final PaymentProvider provider;

    public PendingPaymentSweeper(JdbcClient db, PaymentsService payments, PaymentProvider provider) {
        this.db = db;
        this.payments = payments;
        this.provider = provider;
    }

    private record Stale(String orderId, String paymentIntentId) {
    }

    @Scheduled(fixedDelayString = "${app.payments.pending-sweep-delay-ms:300000}")
    public void expireAbandonedPendingOrders() {
        List<Stale> stale = db.sql("""
                SELECT id, "paymentIntentId" FROM "Order"
                WHERE status = 'pending' AND "paymentIntentId" IS NOT NULL
                  AND "createdAt" < now() - make_interval(mins => :expiryMinutes)
                LIMIT 100
                """)
                .param("expiryMinutes", 30)
                .query((rs, n) -> new Stale(rs.getString("id"), rs.getString("paymentIntentId")))
                .list();
        for (Stale order : stale) {
            if (provider.tryCancelIntent(order.paymentIntentId())) {
                payments.releaseFailedPayment(order.paymentIntentId(), "expired");
            }
        }
    }
}
