package dev.tylerpac.backend.downloads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DownloadPartsTest {

    @Test
    void filesGoInTheStandardModLayout() {
        assertEquals("@OverKillDevCore/addons", DownloadParts.folderFor("@OverKillDevCore", "DankAmmo.pbo"));
        assertEquals("@OverKillDevCore/addons", DownloadParts.folderFor("@OverKillDevCore", "DankAmmo.pbo.OverKill.bisign"));
        assertEquals("@OverKillDevCore/keys", DownloadParts.folderFor("@OverKillDevCore", "OverKill.bikey"));
        assertEquals("@OverKillDevCore", DownloadParts.folderFor("@OverKillDevCore", "mod.cpp"));
        assertNull(DownloadParts.folderFor("@OverKillDevCore", "everything.zip"), "zips are merged as they are");
        assertNull(DownloadParts.folderFor(null, "DankAmmo.pbo"));
    }

    @Test
    void theSameFileNameAlwaysMapsToTheSameEntry() {
        assertEquals("core-client--dankammo-pbo", DownloadParts.entryKey("core", "client", "DankAmmo.pbo"));
        assertEquals(DownloadParts.entryKey("core", "client", "DankAmmo.pbo"), DownloadParts.entryKey("core", "client", "dankammo.pbo"));
        String longOne = DownloadParts.entryKey("overkill-dynamic-crates", "server", "A_Really_Long_Server_Config_File_Name_Version_2.pbo");
        String longTwo = DownloadParts.entryKey("overkill-dynamic-crates", "server", "A_Really_Long_Server_Config_File_Name_Version_3.pbo");
        assertTrue(longOne.length() <= 64, longOne);
        assertNotEquals(longOne, longTwo, "long names that differ late must stay different");
    }

    @Test
    void keysParseIntoGroupPartAndFile() {
        assertEquals(new DownloadParts.Parsed("core", "client", null), DownloadParts.parse("core-client"));
        assertEquals(new DownloadParts.Parsed("core", "client", "dankammo-pbo"), DownloadParts.parse("core-client--dankammo-pbo"));
        assertEquals(new DownloadParts.Parsed("core", "client", "x-server"), DownloadParts.parse("core-client--x-server"));
        assertEquals(new DownloadParts.Parsed("dynamic-servers", "client", null), DownloadParts.parse("dynamic-servers-client"));
        assertNull(DownloadParts.parse("core"));
    }

    @Test
    void groupKeysThatCouldClashAreRefused() {
        assertTrue(DownloadParts.endsWithPart("core-client"));
        assertTrue(DownloadParts.endsWithPart("core--x"));
        assertEquals("@OverKillDevCore", DownloadParts.defaultModFolder("OverKill Dev Core"));
    }
}
