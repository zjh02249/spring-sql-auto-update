package com.cbkj.infrastructure.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChecksumCalculatorTest {

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

    @Test
    public void testCalculateEmptyString() {
        int checksum = ChecksumCalculator.calculate("");
        // Empty string has CRC32 of 0
        assertEquals(0, checksum);
    }

    @Test
    public void testCalculateNullString() {
        int checksum = ChecksumCalculator.calculate((String) null);
        assertEquals(0, checksum);
    }

    @Test
    public void testCalculateNullBytes() {
        int checksum = ChecksumCalculator.calculate((byte[]) null);
        assertEquals(0, checksum);
    }

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

    @Test
    public void testConsistencyWithStringAndBytes() {
        String content = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100));";
        
        int checksumFromString = ChecksumCalculator.calculate(content);
        int checksumFromBytes = ChecksumCalculator.calculate(content.getBytes());
        
        assertEquals(checksumFromString, checksumFromBytes);
    }
}
