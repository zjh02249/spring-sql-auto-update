package com.cbkj.infrastructure.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class MigrationVersionTest {

    @Test
    public void testParseValidVersion() {
        MigrationVersion v1 = MigrationVersion.parse("1.0.0");
        assertEquals("1.0.0", v1.getRawVersion());

        MigrationVersion v2 = MigrationVersion.parse("2.0.0.3");
        assertEquals("2.0.0.3", v2.getRawVersion());

        MigrationVersion v3 = MigrationVersion.parse("1");
        assertEquals("1", v3.getRawVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullVersion() {
        MigrationVersion.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseEmptyVersion() {
        MigrationVersion.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseInvalidVersionWithLetters() {
        MigrationVersion.parse("1.0.a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseInvalidVersionWithHyphen() {
        MigrationVersion.parse("1.0-1");
    }

    @Test
    public void testCompareTo() {
        MigrationVersion v1 = MigrationVersion.parse("1.0.0");
        MigrationVersion v2 = MigrationVersion.parse("1.0.1");
        MigrationVersion v3 = MigrationVersion.parse("1.1.0");
        MigrationVersion v4 = MigrationVersion.parse("2.0.0");
        MigrationVersion v5 = MigrationVersion.parse("1.0.0");

        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v3) < 0);
        assertTrue(v3.compareTo(v4) < 0);
        assertEquals(0, v1.compareTo(v5));
        assertTrue(v4.compareTo(v1) > 0);
    }

    @Test
    public void testCompareToWithDifferentSegmentCount() {
        MigrationVersion v1 = MigrationVersion.parse("1.0");
        MigrationVersion v2 = MigrationVersion.parse("1.0.0");
        MigrationVersion v3 = MigrationVersion.parse("1.0.1");

        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v3) < 0);
    }

    @Test
    public void testEqualsAndHashCode() {
        MigrationVersion v1 = MigrationVersion.parse("1.0.0");
        MigrationVersion v2 = MigrationVersion.parse("1.0.0");
        MigrationVersion v3 = MigrationVersion.parse("1.0.1");

        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertNotEquals(v1, v3);
    }

    @Test
    public void testGetSegments() {
        MigrationVersion v1 = MigrationVersion.parse("1.2.3");
        int[] segments = v1.getSegments();
        assertEquals(3, segments.length);
        assertEquals(1, segments[0]);
        assertEquals(2, segments[1]);
        assertEquals(3, segments[2]);
    }
}
