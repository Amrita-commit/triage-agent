package dev.copilot.demo.web;

import dev.copilot.demo.fault.FaultState;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The demo shop's business endpoints. Every request runs the injected faults first. */
@RestController
public class ShopController {

    private static final Logger log = LoggerFactory.getLogger(ShopController.class);

    private static final List<Map<String, Object>> CATALOG = List.of(
            Map.of("sku", "SKU-1", "name", "Widget", "stock", 42),
            Map.of("sku", "SKU-2", "name", "Gadget", "stock", 7),
            Map.of("sku", "SKU-3", "name", "Gizmo", "stock", 0));

    private final FaultState faults;

    public ShopController(FaultState faults) {
        this.faults = faults;
    }

    @Timed(value = "shop.inventory", description = "Time spent serving inventory", histogram = true)
    @GetMapping("/inventory")
    public List<Map<String, Object>> inventory() {
        faults.applyFaults();
        log.info("Served inventory ({} items)", CATALOG.size());
        return CATALOG;
    }

    @Timed(value = "shop.checkout", description = "Time spent serving checkout", histogram = true)
    @PostMapping("/checkout")
    public Map<String, Object> checkout() {
        faults.applyFaults();
        String orderId = UUID.randomUUID().toString();
        log.info("Checkout completed, orderId={}", orderId);
        return Map.of("orderId", orderId, "status", "CONFIRMED");
    }
}
