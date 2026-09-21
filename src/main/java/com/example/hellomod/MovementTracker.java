package com.example.hellomod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Used only from the server thread. Baselines are the last reported positions. */
final class MovementTracker {
    record Position(double x, double y, double z, String dimension) {}
    record Movement(Position from, Position to, boolean dimensionChanged, double distance) {}

    private final Map<UUID, Position> positions = new HashMap<>();

    void reset(UUID player, Position position) {
        positions.put(player, position);
    }

    Movement sample(UUID player, Position current) {
        Position previous = positions.putIfAbsent(player, current);
        if (previous == null) return null;
        boolean changed = !previous.dimension().equals(current.dimension());
        double dx = current.x() - previous.x();
        double dy = current.y() - previous.y();
        double dz = current.z() - previous.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!changed && distance < 1.0) return null;
        positions.put(player, current);
        return new Movement(previous, current, changed, changed ? 0 : distance);
    }

    void remove(UUID player) { positions.remove(player); }
    void clear() { positions.clear(); }
}
