package com.digital.video.gateway.driver.livox.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents a parsed Livox SDK Packet
 */
public class SdkPacket {

    // Header
    public byte sof;
    public byte version;
    public int length; // uint16
    public long seqNum; // uint32
    public int cmdId; // uint16
    public byte cmdType;
    public byte senderType;
    public byte[] rsvd = new byte[6];
    public int crc16; // uint16

    // Body / Meta
    public int crc32; // uint32
    public byte[] payload;

    // Helper to wrap raw bytes
    public static SdkPacket fromBytes(byte[] data) {
        if (data.length < 24)
            return null;

        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        SdkPacket pkt = new SdkPacket();

        pkt.sof = bb.get();
        if (pkt.sof != SdkProtocol.SOF)
            return null;

        pkt.version = bb.get();
        pkt.length = bb.getShort() & 0xFFFF;
        pkt.seqNum = bb.getInt() & 0xFFFFFFFFL;
        pkt.cmdId = bb.getShort() & 0xFFFF;
        pkt.cmdType = bb.get();
        pkt.senderType = bb.get();
        bb.get(pkt.rsvd);
        pkt.crc16 = bb.getShort() & 0xFFFF;
        pkt.crc32 = bb.getInt(); // 4 bytes at offset 20

        // Payload
        int payloadLen = pkt.length - 24;
        if (payloadLen > 0 && bb.remaining() >= payloadLen) {
            pkt.payload = new byte[payloadLen];
            bb.get(pkt.payload);
        } else {
            pkt.payload = new byte[0];
        }

        return pkt;
    }
}
