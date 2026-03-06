package com.cbkj.infrastructure.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.*;

public class ChecksumCalculatorTest {

    /**
     * 验证相同字符串内容会生成相同校验和，不同内容会生成不同校验和。
     */
    @Test
    public void testCalculateString() {
        String content1 = "CREATE TABLE users (id INT PRIMARY KEY);";
        String content2 = "CREATE TABLE users (id INT PRIMARY KEY);";
        String content3 = "CREATE TABLE products (id INT PRIMARY KEY);";

        int checksum1 = ChecksumCalculator.calculate(content1);
        int checksum2 = ChecksumCalculator.calculate(content2);
        int checksum3 = ChecksumCalculator.calculate(content3);

        // Same content should have same checksum
        assertEquals(checksum1, checksum2);

        // Different content should have different checksum (with high probability)
        assertNotEquals(checksum1, checksum3);
    }

    /**
     * 验证空字符串的 CRC32 结果为 0。
     */
    @Test
    public void testCalculateEmptyString() {
        int checksum = ChecksumCalculator.calculate("");
        // Empty string has CRC32 of 0
        assertEquals(0, checksum);
    }

    /**
     * 验证 null 字符串输入会返回 0。
     */
    @Test
    public void testCalculateNullString() {
        int checksum = ChecksumCalculator.calculate((String) null);
        assertEquals(0, checksum);
    }

    /**
     * 验证 null 字节数组输入会返回 0。
     */
    @Test
    public void testCalculateNullBytes() {
        int checksum = ChecksumCalculator.calculate((byte[]) null);
        assertEquals(0, checksum);
    }

    /**
     * 验证字节数组输入的 CRC32 计算结果稳定。
     */
    @Test
    public void testCalculateByteArray() {
        byte[] bytes1 = "CREATE TABLE test (id INT);".getBytes();
        byte[] bytes2 = "CREATE TABLE test (id INT);".getBytes();
        byte[] bytes3 = "CREATE TABLE other (id INT);".getBytes();

        int checksum1 = ChecksumCalculator.calculate(bytes1);
        int checksum2 = ChecksumCalculator.calculate(bytes2);
        int checksum3 = ChecksumCalculator.calculate(bytes3);

        assertEquals(checksum1, checksum2);
        assertNotEquals(checksum1, checksum3);
    }

    /**
     * 验证字符串和对应字节数组的校验和结果一致。
     */
    @Test
    public void testConsistencyWithStringAndBytes() {
        String content = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100));";
        
        int checksumFromString = ChecksumCalculator.calculate(content);
        int checksumFromBytes = ChecksumCalculator.calculate(content.getBytes());
        
        assertEquals(checksumFromString, checksumFromBytes);
    }

    /**
     * 验证 InputStream 输入的 CRC32 结果与字节数组一致。
     */
    @Test
    public void testCalculateInputStream() throws Exception {
        byte[] bytes = "CREATE TABLE stream_test (id INT PRIMARY KEY);".getBytes("UTF-8");
        int checksumFromStream = ChecksumCalculator.calculate(new ByteArrayInputStream(bytes));
        int checksumFromBytes = ChecksumCalculator.calculate(bytes);

        assertEquals(checksumFromBytes, checksumFromStream);
    }

    /**
     * 验证 null 输入流会返回 0。
     */
    @Test
    public void testCalculateNullInputStream() throws Exception {
        assertEquals(0, ChecksumCalculator.calculate((ByteArrayInputStream) null));
    }
}
