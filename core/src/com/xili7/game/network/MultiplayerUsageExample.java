package com.xili7.game.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.xili7.game.network.MessageParser.PlayerSnapshot;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example-only integration snippet showing how to plug the networking layer
 * into an existing LibGDX game loop without touching your offline logic.
 *
 * This class is intentionally standalone so you can copy only the relevant
 * parts into your current GameScreen/update system.
 */
public class MultiplayerUsageExample {
    private final Map<String, PlayerSnapshot> remotePlayers = new ConcurrentHashMap<>();

    // Replace with your existing local player values from offline game logic.
    private final Vector2 localPlayerPosition = new Vector2();
    private String localMovementState = "IDLE";

    private Client client;

    public void startNetworking() {
        client = new Client("127.0.0.1", 7777, new Client.Listener() {
            @Override
            public void onConnected(String playerId) {
                Gdx.app.log("NET", "Connected as " + playerId);
            }

            @Override
            public void onStateReceived(Map<String, PlayerSnapshot> players) {
                // Callback runs on background thread.
                // Store in thread-safe map and consume in render/update thread.
                remotePlayers.clear();
                remotePlayers.putAll(players);
            }

            @Override
            public void onDisconnected() {
                Gdx.app.log("NET", "Disconnected from server");
            }

            @Override
            public void onError(Exception exception) {
                Gdx.app.error("NET", "Networking error", exception);
            }
        });

        try {
            client.connect();
        } catch (IOException e) {
            Gdx.app.error("NET", "Failed to connect", e);
        }
    }

    /**
     * Call inside your existing update/render loop.
     * Offline physics and controls stay unchanged; this only mirrors data online.
     */
    public void update(float delta) {
        // 1) Run your existing local/offline gameplay update first.
        // localPlayerPosition and localMovementState should come from that logic.

        // 2) Send local player snapshot to server.
        if (client != null && client.isConnected()) {
            client.sendInput(localPlayerPosition.x, localPlayerPosition.y, localMovementState);
        }

        // 3) Apply remote state to your existing rendering entities.
        for (PlayerSnapshot snapshot : remotePlayers.values()) {
            if (snapshot.playerId().equals(client.localPlayerId())) {
                continue; // Skip local player (already rendered by offline logic).
            }

            // Example hook:
            // updateRemoteBird(snapshot.playerId(), snapshot.x(), snapshot.y(), snapshot.state());
        }
    }

    public void dispose() {
        if (client != null) {
            client.disconnect();
        }
    }
}
