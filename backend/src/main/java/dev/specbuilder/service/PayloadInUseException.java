package dev.specbuilder.service;

import dev.specbuilder.api.dto.PayloadUsage;

import java.util.List;

public class PayloadInUseException extends RuntimeException {

    private final List<PayloadUsage> usages;

    public PayloadInUseException(String payloadName, List<PayloadUsage> usages) {
        super(payloadName + " is still used in " + usages.size() + (usages.size() == 1 ? " place" : " places")
                + "; remove those references first");
        this.usages = List.copyOf(usages);
    }

    public List<PayloadUsage> usages() {
        return usages;
    }
}
