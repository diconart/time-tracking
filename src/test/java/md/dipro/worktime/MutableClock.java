package md.dipro.worktime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> current = new AtomicReference<>();

    public MutableClock(Instant initial) { current.set(initial); }
    public void set(Instant instant) { current.set(instant); }
    public void advance(Duration duration) { current.updateAndGet(at -> at.plus(duration)); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return current.get(); }
}
