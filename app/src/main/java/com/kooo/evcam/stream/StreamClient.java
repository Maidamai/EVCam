package com.kooo.evcam.stream;

import com.kooo.evcam.AppLog;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端推流：EVCam 主动连接 ESP32 推流。
 * <p>
 * 支持：
 * <ul>
 *   <li>TCP：帧格式 {@code [4B 小端 uint32 len][JPEG]}，断线自动重连</li>
 *   <li>UDP：16B 分片头 + ≤1400B 数据，无连接</li>
 * </ul>
 * 协议格式与 Python 脚本一致，ESP32 端不用改代码。
 * <p>
 * 实现 {@link FrameStreamServer} 接口以复用 {@link MjpegStreamManager} 的多态逻辑：
 * {@code startServer()} = 建立连接，{@code getClientCount()} = 1 表示已连接。
 */
public class StreamClient implements FrameStreamServer {
    private static final String TAG = "StreamClient";
    private static final int RECONNECT_INTERVAL_MS = 2000;
    private static final int UDP_MAGIC = 0x5354;
    private static final int UDP_CHUNK_SIZE = 1400;

    private final String host;
    private final int port;
    private final String protocol;
    private final MjpegFrameHolder frameHolder;

    private Thread clientThread;
    private volatile boolean stopped = false;
    private final AtomicInteger connected = new AtomicInteger(0);

    public StreamClient(String host, int port, String protocol, MjpegFrameHolder frameHolder) {
        this.host = host;
        this.port = port;
        this.protocol = protocol != null ? protocol.toUpperCase() : "TCP";
        this.frameHolder = frameHolder;
    }

    @Override
    public void startServer() {
        stopped = false;
        clientThread = new Thread(this::clientLoop, "StreamClient-" + protocol);
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void clientLoop() {
        if ("UDP".equals(protocol)) {
            udpLoop();
        } else {
            tcpLoop();
        }
    }

    /** TCP 模式：建立连接 → 持续推帧 → 断线重连。 */
    private void tcpLoop() {
        byte[] lastSent = null;
        while (!stopped) {
            Socket socket = null;
            try {
                AppLog.i(TAG, "TCP connecting to " + host + ":" + port);
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setTcpNoDelay(true);
                connected.set(1);
                lastSent = null;
                AppLog.i(TAG, "TCP connected to " + host + ":" + port);

                OutputStream out = socket.getOutputStream();
                while (!stopped && !socket.isClosed()) {
                    byte[] jpg = frameHolder.get();
                    if (jpg == null || jpg == lastSent) {
                        try { Thread.sleep(5); } catch (InterruptedException ie) { break; }
                        continue;
                    }
                    lastSent = jpg;
                    byte[] header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(jpg.length).array();
                    out.write(header);
                    out.write(jpg);
                    out.flush();
                }
            } catch (Exception e) {
                if (!stopped) {
                    AppLog.w(TAG, "TCP error: " + e.getMessage()
                            + ", reconnecting in " + RECONNECT_INTERVAL_MS + "ms");
                }
            } finally {
                connected.set(0);
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
            // 重连等待
            if (stopped) break;
            try {
                Thread.sleep(RECONNECT_INTERVAL_MS);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    /** UDP 模式：直接 sendto 目标地址，无连接概念。 */
    private void udpLoop() {
        byte[] lastSent = null;
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket();
            InetSocketAddress target = new InetSocketAddress(host, port);
            connected.set(1);
            AppLog.i(TAG, "UDP target " + host + ":" + port);

            while (!stopped) {
                byte[] jpg = frameHolder.get();
                if (jpg == null || jpg == lastSent) {
                    try { Thread.sleep(5); } catch (InterruptedException ie) { break; }
                    continue;
                }
                lastSent = jpg;
                sendUdpFrame(sock, target, jpg);
            }
        } catch (Exception e) {
            if (!stopped) AppLog.w(TAG, "UDP error: " + e.getMessage());
        } finally {
            connected.set(0);
            if (sock != null) sock.close();
        }
    }

    private void sendUdpFrame(DatagramSocket sock, InetSocketAddress target, byte[] jpg) throws IOException {
        int total = jpg.length;
        int n = (total + UDP_CHUNK_SIZE - 1) / UDP_CHUNK_SIZE;
        for (int i = 0; i < n; i++) {
            int off = i * UDP_CHUNK_SIZE;
            int len = (i == n - 1) ? (total - off) : UDP_CHUNK_SIZE;
            byte[] packet = new byte[16 + len];
            ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) UDP_MAGIC)
                    .putShort((short) 0)
                    .putInt(total)
                    .putInt(i)
                    .putInt(n);
            System.arraycopy(jpg, off, packet, 16, len);
            sock.send(new DatagramPacket(packet, packet.length, target));
        }
    }

    @Override
    public void stopServer() {
        stopped = true;
        connected.set(0);
        if (clientThread != null) clientThread.interrupt();
    }

    @Override
    public int getClientCount() {
        return connected.get();
    }
}
