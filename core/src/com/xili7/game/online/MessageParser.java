package com.xili7.game.online;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Protocol utility for plain text TCP messages.
 *
 * All messages use the same pipe-separated format:
 * COMMAND|arg1|arg2|...
 */
public final class MessageParser {
    public static final String DELIMITER = "|";

    private MessageParser() {
    }

    public record ParsedMessage(String command, List<String> args) {
        public String arg(int index) {
            return args.get(index);
        }

        public int size() {
            return args.size();
        }
    }

    public record PlayerState(String playerId, float x, float y, int score) {
    }

    public static ParsedMessage parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            throw new IllegalArgumentException("Cannot parse empty message");
        }

        String[] tokens = rawLine.split("\\|", -1);
        String command = tokens[0].trim().toUpperCase(Locale.ROOT);
        List<String> args = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            args.add(tokens[i]);
        }
        return new ParsedMessage(command, List.copyOf(args));
    }

    public static String serialize(String command, Object... args) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        joiner.add(command);
        for (Object arg : args) {
            joiner.add(arg == null ? "" : String.valueOf(arg));
        }
        return joiner.toString();
    }

    public static String join() {
        return serialize("JOIN");
    }

    public static String createRoom() {
        return serialize("CREATE_ROOM");
    }

    public static String joinRoom(String roomId) {
        return serialize("JOIN_ROOM", roomId);
    }

    public static String roomCreated(String roomId) {
        return serialize("ROOM_CREATED", roomId);
    }

    public static String roomJoined(String roomId) {
        return serialize("ROOM_JOINED", roomId);
    }

    public static String start() {
        return serialize("START");
    }

    public static String welcome(String playerId) {
        return serialize("WELCOME", playerId);
    }

    public static String jump(String playerId) {
        return serialize("JUMP", playerId);
    }

    public static String state(String playerId, float x, float y, int score) {
        return serialize("STATE", playerId, x, y, score);
    }

    public static String left(String playerId) {
        return serialize("LEFT", playerId);
    }

    public static String bulkState(List<PlayerState> states) {
        StringJoiner joiner = new StringJoiner(DELIMITER);
        joiner.add("BULK_STATE");
        joiner.add(Integer.toString(states.size()));
        for (PlayerState state : states) {
            joiner.add(state.playerId());
            joiner.add(Float.toString(state.x()));
            joiner.add(Float.toString(state.y()));
            joiner.add(Integer.toString(state.score()));
        }
        return joiner.toString();
    }

    public static PlayerState parseState(ParsedMessage message) {
        if (!"STATE".equals(message.command()) || message.size() < 4) {
            throw new IllegalArgumentException("Invalid STATE message: " + message);
        }
        return new PlayerState(
            message.arg(0),
            Float.parseFloat(message.arg(1)),
            Float.parseFloat(message.arg(2)),
            Integer.parseInt(message.arg(3))
        );
    }

    public static List<PlayerState> parseBulkState(ParsedMessage message) {
        if (!"BULK_STATE".equals(message.command()) || message.size() < 1) {
            throw new IllegalArgumentException("Invalid BULK_STATE message: " + message);
        }

        int count = Integer.parseInt(message.arg(0));
        int expected = 1 + (count * 4);
        if (message.size() < expected) {
            throw new IllegalArgumentException("Incomplete BULK_STATE message: " + message);
        }

        List<PlayerState> states = new ArrayList<>(count);
        int cursor = 1;
        for (int i = 0; i < count; i++) {
            states.add(new PlayerState(
                message.arg(cursor++),
                Float.parseFloat(message.arg(cursor++)),
                Float.parseFloat(message.arg(cursor++)),
                Integer.parseInt(message.arg(cursor++))
            ));
        }
        return states;
    }
}
