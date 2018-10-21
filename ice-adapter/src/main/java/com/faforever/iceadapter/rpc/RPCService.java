package com.faforever.iceadapter.rpc;

import com.faforever.iceadapter.IceAdapter;
import com.faforever.iceadapter.ice.CandidatesMessage;
import com.google.gson.Gson;
import com.nbarraille.jjsonrpc.JJsonPeer;
import com.nbarraille.jjsonrpc.TcpServer;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.faforever.iceadapter.debug.Debug.debug;

/**
 * Handles communication between client and adapter, opens a server for the client to connect to
 */
@Slf4j
public class RPCService {

    private static Gson gson = new Gson();

    private static TcpServer tcpServer;
    private static RPCHandler rpcHandler;

    public static void init() {
        log.info("Creating RPC server on port {}", IceAdapter.RPC_PORT);

        rpcHandler = new RPCHandler();
        tcpServer = new TcpServer(IceAdapter.RPC_PORT, rpcHandler);
        tcpServer.start();

        debug().rpcStarted(tcpServer.getFirstPeer());
        tcpServer.getFirstPeer().thenAccept(firstPeer -> {
            firstPeer.onConnectionLost(() -> {
                log.info("Lost connection to first RPC Peer. Stopping adapter...");
                IceAdapter.close();
            });
        });
    }

    public static void onConnectionStateChanged(String newState) {
        getPeerOrWait().sendNotification("onConnectionStateChanged", Arrays.asList(newState));
    }

    public static void onGpgNetMessageReceived(String header, List<Object> chunks) {
        getPeerOrWait().sendNotification("onGpgNetMessageReceived", Arrays.asList(header, chunks));
    }

    public static void onIceMsg(CandidatesMessage candidatesMessage) {
        getPeerOrWait().sendNotification("onIceMsg", Arrays.asList(candidatesMessage.getSrcId(), candidatesMessage.getDestId(), gson.toJson(candidatesMessage)));
    }

    public static void onIceConnectionStateChanged(long localPlayerId, long remotePlayerId, String state) {
        getPeerOrWait().sendNotification("onIceConnectionStateChanged", Arrays.asList(localPlayerId, remotePlayerId, state));
    }

    public static void onConnected(long localPlayerId, long remotePlayerId, boolean connected) {
        getPeerOrWait().sendNotification("onConnected", Arrays.asList(localPlayerId, remotePlayerId, connected));
    }

    /**
     * Blocks until a peer is connected (the client)
     * @return the currently connected peer (the client)
     */
    public static JJsonPeer getPeerOrWait() {
        try {
            return tcpServer.getFirstPeer().get();
        } catch (Exception e) {
            log.error("Error on fetching first peer", e);
        }
        return null;
    }

    public static CompletableFuture<JJsonPeer> getPeerFuture() {
        return tcpServer.getFirstPeer();
    }

    public static void close() {
        tcpServer.stop();
    }
}
