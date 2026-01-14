package com.digital.video.gateway.driver.livox;

import com.digital.video.gateway.driver.livox.codec.PacketDecoder;
import com.digital.video.gateway.driver.livox.protocol.PacketCRC;
import com.digital.video.gateway.driver.livox.protocol.SdkPacket;
import com.digital.video.gateway.driver.livox.protocol.SdkProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Livox Driver - Manages UDP connections to Lidars
 */
public class LivoxDriver {

    private static final Logger log = LoggerFactory.getLogger(LivoxDriver.class);

    private static final int DISCOVERY_PORT = 55000;
    private static final int CMD_PORT = 56000; // Port on Lidar to send commands to

    private EventLoopGroup workerGroup;
    private Channel discoveryChannel;

    // Track connected devices?
    private final ConcurrentHashMap<String, InetSocketAddress> devices = new ConcurrentHashMap<>();

    public void start() {
        log.info("Starting Livox Driver (Netty)...");
        workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) throws Exception {
                            ch.pipeline().addLast(new PacketDecoder());
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<SdkPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, SdkPacket packet)
                                        throws Exception {
                                    handlePacket(ctx, packet);
                                }
                            });
                        }
                    });

            // Bind to 55000 to listen for Broadcasts
            discoveryChannel = b.bind(DISCOVERY_PORT).sync().channel();
            log.info("Listening for Livox Broadcasts on port {}", DISCOVERY_PORT);

            // Allow sending from this channel too (to Lidar:56000)

        } catch (InterruptedException e) {
            log.error("Driver interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    // Callback interface
    public interface PointCloudCallback {
        void onPointCloud(SdkPacket packet);
    }

    private PointCloudCallback pointCloudCallback;

    public void setPointCloudCallback(PointCloudCallback callback) {
        this.pointCloudCallback = callback;
    }

    private void handlePacket(ChannelHandlerContext ctx, SdkPacket packet) {
        // log.debug("RX Packet: Type={}, CmdId={}", packet.cmdType, packet.cmdId);

        // Check if it's data
        if (packet.cmdType == SdkProtocol.CMD_TYPE_MSG) {
            // Should check specific CmdId for Point Cloud?
            // In SDK2, Point Cloud is usually Msg?
            // Or Payload inside Msg.
            if (pointCloudCallback != null) {
                pointCloudCallback.onPointCloud(packet);
            }
        }

        // Handle Discovery / Broadcasts
        // TODO: Map logic
    }

    /**
     * Send a Command to a specific Lidar
     */
    public void sendCommand(String ip, int cmdId, byte[] payload) {
        if (discoveryChannel == null)
            return;

        // Construct Packet
        ByteBuf buf = buildPacketBuff(cmdId, payload);
        InetSocketAddress target = new InetSocketAddress(ip, CMD_PORT);

        discoveryChannel.writeAndFlush(new DatagramPacket(buf, target));
    }

    private ByteBuf buildPacketBuff(int cmdId, byte[] payload) {
        int payloadLen = (payload != null) ? payload.length : 0;
        int totalLen = SdkProtocol.WRAPPER_LEN + payloadLen;

        byte[] buf = new byte[totalLen];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

        // Header
        bb.put(SdkProtocol.SOF);
        bb.put(SdkProtocol.VER_MID360); // Use 0x00 for Mid-360
        bb.putShort((short) totalLen);
        bb.putInt(getNextSeq()); // Seq
        bb.putShort((short) cmdId);
        bb.put(SdkProtocol.CMD_TYPE_REQ);
        bb.put(SdkProtocol.SENDER_HOST);
        bb.put(new byte[6]); // Rsvd

        // CRC16
        bb.putShort((short) PacketCRC.crc16(buf, 0, SdkProtocol.HEADER_LEN));

        // CRC32
        int crc32 = 0;
        if (payloadLen > 0) {
            crc32 = PacketCRC.crc32(payload, 0, payloadLen);
        }
        bb.putInt(crc32);

        // Payload
        if (payloadLen > 0) {
            bb.put(payload);
        }

        return Unpooled.wrappedBuffer(buf);
    }

    private int seqCounter = 0;

    private synchronized int getNextSeq() {
        return seqCounter++;
    }
}
