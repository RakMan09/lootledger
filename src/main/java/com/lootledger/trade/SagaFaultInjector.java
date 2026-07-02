package com.lootledger.trade;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Test seam that lets crash-injection tests simulate a process death at a specific saga step.
 * In production nothing is ever armed, so this is a no-op.
 */
@Component
public class SagaFaultInjector {

    /** Thrown to simulate an abrupt failure between saga steps. */
    public static class InjectedCrash extends RuntimeException {
        public InjectedCrash(String point) {
            super("Injected crash at saga point: " + point);
        }
    }

    private final AtomicReference<String> armedPoint = new AtomicReference<>(null);

    /** Arm a crash that fires once when the given point is reached, then disarms. */
    public void armCrashAt(String point) {
        armedPoint.set(point);
    }

    public void disarm() {
        armedPoint.set(null);
    }

    public void maybeCrash(String point) {
        if (point.equals(armedPoint.get())) {
            armedPoint.set(null);
            throw new InjectedCrash(point);
        }
    }
}
