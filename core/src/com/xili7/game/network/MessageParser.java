package com.xili7.game.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Utility class for serializing/deserializing plain-text socket messages.
 *
 * Format used by all messages:
 * COMMAND|param1|param2|...
 */
public final class MessageParser {
    public static final String DELIMITER = "|";

    private MessageParser() {
    }

    /**
     * Parsed message representation for incoming socket data.
     */
    public static final class ParsedMessage {
        private final String command;
        private final List<String> params;

        public ParsedMessage(String command, List<String> params) {
            this.command = command;
            this.params = List.copyOf(params);
        }

        public String command() {
            return command;
        }

        public List<String> params() {
            return params;
        }

        public String param(int index) {
            return params.get(index);
        }

        public int paramCount() {
            return params.size();
        }
    }

    /**
     * DTO describing a single player snapshot inside a STATE message.
     */
    public static final class PlayerSnapshot {
        private final String playerId;
        private final float x;
        private final float y;
        private final String state;

        public PlayerSnapshot(String playerId, float x, float y, String state) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.x = x;
            this.y = y;
            this.state = Objects.requireNonNullElse(state, "");
        }

        public String playerId() {
            return playerId;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public String state() {
            return state;
        }
    }

    public static ParsedMessage parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            throw new IllegalArgumentException("Cannot parse blank message");
        }

        String[] tokens = rawLine.split("\\|", -1);
        String command = tokens[0].trim();
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Message command is missing: " + rawLine);
        }

        List<String> params = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            params.add(tokens[i]);
        }

        return new ParsedMessage(command, params);
    }

    public static String serialize(String command, String... params) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        joiner.add(command);
        for (String param : params) {
            joiner.add(param == null ? "" : param);
        }
        return joiner.toString();
    }

    /**
     * OUTGOING (client->server): INPUT|playerId|x|y|movementState
     */
    public static String serializeInput(String playerId, float x, float y, String movementState) {
        return serialize("INPUT", playerId, Float.toString(x), Float.toString(y), movementState);
    }

    /**
     * OUTGOING (server->client): STATE|count|playerId|x|y|state|playerId|x|y|state...
     */
    public static String serializeState(Map<String, PlayerSnapshot> players) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        joiner.add("STATE");
        joiner.add(Integer.toString(players.size()));

        for (PlayerSnapshot snapshot : players.values()) {
            joiner.add(snapshot.playerId());
            joiner.add(Float.toString(snapshot.x()));
            joiner.add(Float.toString(snapshot.y()));
            joiner.add(snapshot.state());
        }

        return joiner.toString();
    }

    public static Map<String, PlayerSnapshot> parseState(ParsedMessage message) {
        if (!"STATE".equals(message.command())) {
            throw new IllegalArgumentException("Expected STATE command but got " + message.command());
        }

        if (message.paramCount() < 1) {
            throw new IllegalArgumentException("STATE message missing player count");
        }

        int count = Integer.parseInt(message.param(0));
        if (count == 0) {
            return Collections.emptyMap();
        }

        int expectedValues = 1 + (count * 4);
        if (message.paramCount() < expectedValues) {
            throw new IllegalArgumentException("STATE message incomplete. Expected " + expectedValues + " params");
        }

        Map<String, PlayerSnapshot> snapshots = new LinkedHashMap<>();
        int cursor = 1;
        for (int i = 0; i < count; i++) {
            String playerId = message.param(cursor++);
            float x = Float.parseFloat(message.param(cursor++));
            float y = Float.parseFloat(message.param(cursor++));
            String state = message.param(cursor++);
            snapshots.put(playerId, new PlayerSnapshot(playerId, x, y, state));
        }

        return snapshots;
    }
}
