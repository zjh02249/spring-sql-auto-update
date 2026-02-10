package com.cbkj.infrastructure.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Checksum计算工具类
 * 使用CRC32算法计算SQL文件内容的校验和
 */
public class ChecksumCalculator {

    private ChecksumCalculator() {
        // 工具类，禁止实例化
    }

    /**
     * 计算字符串内容的CRC32校验和
     *
     * @param content 字符串内容
     * @return CRC32校验和
     */
    public static int calculate(String content) {
        if (content == null) {
            return 0;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(content.getBytes(StandardCharsets.UTF_8));
        return (int) crc32.getValue();
    }

    /**
     * 计算输入流内容的CRC32校验和
     *
     * @param inputStream 输入流
     * @return CRC32校验和
     * @throws IOException 当读取流失败时
     */
    public static int calculate(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return 0;
        }
        CRC32 crc32 = new CRC32();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            crc32.update(buffer, 0, bytesRead);
        }
        return (int) crc32.getValue();
    }

    /**
     * 计算字节数组的CRC32校验和
     *
     * @param bytes 字节数组
     * @return CRC32校验和
     */
    public static int calculate(byte[] bytes) {
        if (bytes == null) {
            return 0;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return (int) crc32.getValue();
    }
}
