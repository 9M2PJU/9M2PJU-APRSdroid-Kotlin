package org.aprsdroid.app;

import org.junit.Test;

import kotlin.Triple;

import static org.junit.Assert.*;

public class AprsPacketTests {

	@Test
	public void testPasscodeBasic() {
		assertEquals(18403, AprsPacket.passcode("AB1CD"));
	}

	@Test
	public void testPasscodeIgnoresSsid() {
		assertEquals(AprsPacket.passcode("AB1CD"), AprsPacket.passcode("AB1CD-9"));
	}

	@Test
	public void testPasscodeAllowed() {
		assertTrue(AprsPacket.passcodeAllowed("AB1CD", "18403", false));
		assertFalse(AprsPacket.passcodeAllowed("AB1CD", "12345", false));
		assertTrue(AprsPacket.passcodeAllowed("AB1CD", "", true));
		assertFalse(AprsPacket.passcodeAllowed("AB1CD", "", false));
		assertTrue(AprsPacket.passcodeAllowed("AB1CD", "-1", true));
	}

	@Test
	public void testFormatCallSsid() {
		assertEquals("AB1CD", AprsPacket.formatCallSsid("AB1CD", null));
		assertEquals("AB1CD", AprsPacket.formatCallSsid("AB1CD", ""));
		assertEquals("AB1CD-9", AprsPacket.formatCallSsid("AB1CD", "9"));
	}

	@Test
	public void testUnitConversions() {
		assertEquals(328, AprsPacket.m2ft(100.0));
		assertEquals(194, AprsPacket.mps2kt(100.0));
	}

	@Test
	public void testFormatLogin() {
		assertEquals("user AB1CD pass 18403 vers APDR20",
			AprsPacket.formatLogin("AB1CD", "", "18403", "APDR20"));
		assertEquals("user AB1CD-9 pass 18403 vers APDR20",
			AprsPacket.formatLogin("AB1CD", "9", "18403", "APDR20"));
	}

	@Test
	public void testStatusToBits() {
		assertEquals(new Triple<>(1, 1, 1), AprsPacket.statusToBits("Off Duty"));
		assertEquals(new Triple<>(0, 0, 0), AprsPacket.statusToBits("EMERGENCY!"));
		assertEquals(new Triple<>(1, 1, 1), AprsPacket.statusToBits("Unknown"));
	}

	@Test
	public void testDegreesToDdm() {
		kotlin.Pair<Integer, Double> result = AprsPacket.degreesToDDM(48.8583);
		assertEquals(Integer.valueOf(48), result.getFirst());
		assertEquals(51.498, result.getSecond(), 0.001);
	}

	@Test
	public void testMiceLong() {
		Triple<Integer, Integer, Integer> result = AprsPacket.miceLong(48.8583);
		assertEquals(Integer.valueOf(48), result.getFirst());
		assertEquals(Integer.valueOf(51), result.getSecond());
		assertEquals(Integer.valueOf(49), result.getThird());
	}

	@Test
	public void testEncodeDest() {
		String dest = AprsPacket.encodeDest(48.8583, 0, 0, 1, 1, 1, 0);
		assertEquals(6, dest.length());
	}

	@Test
	public void testEncodeInfo() {
		Triple<String, Integer, Integer> info = AprsPacket.encodeInfo(48.8583, 25.0, 90.0, "/[");
		assertEquals(10, info.getFirst().length());
		assertEquals(Integer.valueOf(0), info.getSecond()); // east
	}

	@Test
	public void testAltitude() {
		assertEquals("\"]7}", AprsPacket.altitude(12345.0));
	}

	@Test
	public void testParseQrg() {
		assertEquals("145.500", AprsPacket.parseQrg("QRG 145.500 MHz"));
		assertEquals("145,500", AprsPacket.parseQrg("QRG 145,500 MHz"));
		assertNull(AprsPacket.parseQrg("no qrg here"));
	}

	@Test
	public void testParseHostPort() {
		kotlin.Pair<String, Integer> result = AprsPacket.parseHostPort("aprs.example.com:14580", 10152);
		assertEquals("aprs.example.com", result.getFirst());
		assertEquals(Integer.valueOf(14580), result.getSecond());

		kotlin.Pair<String, Integer> defaultPort = AprsPacket.parseHostPort("aprs.example.com", 14580);
		assertEquals("aprs.example.com", defaultPort.getFirst());
		assertEquals(Integer.valueOf(14580), defaultPort.getSecond());

		kotlin.Pair<String, Integer> badPort = AprsPacket.parseHostPort("aprs.example.com:abc", 14580);
		assertEquals("aprs.example.com", badPort.getFirst());
		assertEquals(Integer.valueOf(14580), badPort.getSecond());
	}
}
