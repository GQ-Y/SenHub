package com.digital.video.gateway.driver.livox.protocol;

import java.util.zip.CRC32;

/**
 * CRC Calculation Utilities for Livox SDK2
 */
public class PacketCRC {

    // Poly 0x1021, Init 0xFFFF
    private static final int[] CRC16_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0)
                    crc = (crc << 1) ^ 0x1021;
                else
                    crc <<= 1;
            }
            CRC16_TABLE[i] = crc & 0xFFFF;
        }
    }

    public static int crc16(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc = (crc >> 8) ^ CRC16_TABLE[(crc ^ data[offset + i]) & 0xFF];
        }
        return crc;
    }

    public static int crc32(byte[] data, int offset, int length) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, offset, length);
        return (int) crc32.getValue();
    }
}
