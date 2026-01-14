package com.digital.video.gateway.driver.livox.protocol;

/**
 * Livox SDK2 Protocol Constants and Packet Structure
 */
public class SdkProtocol {

    // Constants
    public static final byte SOF = (byte) 0xAA;
    public static final int HEADER_LEN = 18; // Bytes before CRC16
    public static final int WRAPPER_LEN = 24; // Header + CRC16 + CRC32 (Assuming CRC32 is at end of header block or end
                                              // of payload?)
    // Based on SDK2 sdk_protocol.h:
    // SdkPacket struct size is "sizeof(SdkPacket)" which includes crc32_d.
    // However, PACKET_WRAPPER_LEN = sizeof(SdkPacket) - 1 (data[1]).
    // which effectively is offset to payload.
    // So Wrapper length (overhead) is indeed 24 bytes.

    // Command Types
    public static final byte CMD_TYPE_REQ = 0x00;
    public static final byte CMD_TYPE_ACK = 0x01;
    public static final byte CMD_TYPE_MSG = 0x02;

    // Sender Types
    public static final byte SENDER_HOST = 0x00;
    public static final byte SENDER_LIDAR = 0x01;

    // Versions
    public static final byte VER_SDK2 = 0x01; // Or 0? SDK source said 0. Let's support both or configurable.
    public static final byte VER_MID360 = 0x00;

}
