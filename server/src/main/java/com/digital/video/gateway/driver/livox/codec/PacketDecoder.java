package com.digital.video.gateway.driver.livox.codec;

import com.digital.video.gateway.driver.livox.protocol.SdkPacket;
import com.digital.video.gateway.driver.livox.protocol.SdkProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes UDP Datagrams into SdkPacket objects.
 */
public class PacketDecoder extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(PacketDecoder.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        ByteBuf content = msg.content();
        int readable = content.readableBytes();

        if (readable < SdkProtocol.WRAPPER_LEN) {
            return; // Too short
        }

        // Copy data to array (simplifies logic for now, optimize with ByteBuf directly
        // later if needed)
        byte[] data = new byte[readable];
        content.readBytes(data);

        // Basic Validation
        if (data[0] != SdkProtocol.SOF) {
            return; // Invalid SOF
        }

        // Parse
        SdkPacket packet = SdkPacket.fromBytes(data);
        if (packet != null) {
            // Need to create a wrapper that includes Sender IP?
            // For now, pass SdkPacket up, assume context/handler tracks session or uses
            // packet.senderType
            ctx.fireChannelRead(packet);
        } else {
            logger.warn("Failed to parse packet from {}", msg.sender());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Decoder exception", cause);
    }
}
