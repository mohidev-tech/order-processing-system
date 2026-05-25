package com.orderprocessing.shared.events;

/**
 * Saga choreography topic names — single source of truth so producers and
 * consumers in different services can't drift on string typos.
 *
 * Happy path:
 *   order.created -> inventory.reserved -> payment.processed -> shipping.completed -> order.completed
 *
 * Failures publish to the .failed topic. The orchestrator (in order-service)
 * listens to .failed events and emits compensation events backwards.
 */
public final class Topics {
    private Topics() {}

    public static final String ORDER_CREATED       = "order.created";
    public static final String ORDER_COMPLETED     = "order.completed";
    public static final String ORDER_CANCELLED     = "order.cancelled";

    public static final String INVENTORY_RESERVE   = "inventory.reserve";   // command
    public static final String INVENTORY_RESERVED  = "inventory.reserved";  // event
    public static final String INVENTORY_FAILED    = "inventory.failed";
    public static final String INVENTORY_RELEASE   = "inventory.release";   // compensation command

    public static final String PAYMENT_PROCESS     = "payment.process";     // command
    public static final String PAYMENT_PROCESSED   = "payment.processed";   // event
    public static final String PAYMENT_FAILED      = "payment.failed";
    public static final String PAYMENT_REFUND      = "payment.refund";      // compensation command

    public static final String SHIPPING_DISPATCH   = "shipping.dispatch";
    public static final String SHIPPING_COMPLETED  = "shipping.completed";
    public static final String SHIPPING_FAILED     = "shipping.failed";
}
