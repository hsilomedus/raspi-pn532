package mk.hsilomedus.pn532;

public class PN532 {

	static final byte PN532_COMMAND_GETFIRMWAREVERSION = 0x02;
	static final byte PN532_COMMAND_SAMCONFIGURATION = 0x14;
	static final byte PN532_COMMAND_INLISTPASSIVETARGET = 0x4A;

	private IPN532Interface medium;
	private byte[] pn532_packetbuffer;

	public PN532(IPN532Interface medium) {
		this.medium = medium;
		this.pn532_packetbuffer = new byte[64];
	}

	public void begin() {
		medium.begin();
		medium.wakeup();
//		byte[] command = new byte[1];
//		command[0] = PN532_COMMAND_GETFIRMWAREVERSION;
//		try {
//			medium.writeCommand(command);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	public long getFirmwareVersion() throws InterruptedException {
		long response;

		byte[] command = new byte[1];
		command[0] = PN532_COMMAND_GETFIRMWAREVERSION;

		if (medium.writeCommand(command) != CommandStatus.OK) {
			return 0;
		}

		// read data packet
//		int status = medium.readResponse(pn532_packetbuffer,
//				pn532_packetbuffer.length);
		int status = medium.readResponse(pn532_packetbuffer,
				12);
		if (0 > status) {
			return 0;
		}
		
		int offset = 6;

		response = pn532_packetbuffer[offset + 0];
		response <<= 8;
		response |= pn532_packetbuffer[offset + 1];
		response <<= 8;
		response |= pn532_packetbuffer[offset + 2];
		response <<= 8;
		response |= pn532_packetbuffer[offset + 3];

		return response;
	}

	public boolean SAMConfig() throws InterruptedException {
		byte[] command = new byte[4];
		command[0] = PN532_COMMAND_SAMCONFIGURATION;
		command[1] = 0x01; // normal mode;
		command[2] = 0x14; // timeout 50ms * 20 = 1 second
		command[3] = 0x01; // use IRQ pin!

		if (medium.writeCommand(command) != CommandStatus.OK) {
			return false;
		}

//		return 0 < medium.readResponse(pn532_packetbuffer,
//				pn532_packetbuffer.length);
		return 0 < medium.readResponse(pn532_packetbuffer,
				8);
	}

	public int readPassiveTargetID(byte cardbaudrate, byte[] buffer) throws InterruptedException {
		byte[] command = new byte[3];
		command[0] = PN532_COMMAND_INLISTPASSIVETARGET;
		command[1] = 1; // max 1 cards at once (we can set this to 2 later)
		command[2] = (byte) cardbaudrate;

		if (medium.writeCommand(command) != CommandStatus.OK) {
			return -1; // command failed
		}

		// read data packet
//		if (medium.readResponse(pn532_packetbuffer, pn532_packetbuffer.length) < 0) {
		if (medium.readResponse(pn532_packetbuffer, 20) < 0) {
			return -1;
		}

		// check some basic stuff
		/*
		 * ISO14443A card response should be in the following format:
		 * 
		 * byte Description -------------
		 * ------------------------------------------ b0 Tags Found b1 Tag
		 * Number (only one used in this example) b2..3 SENS_RES b4 SEL_RES b5
		 * NFCID Length b6..NFCIDLen NFCID
		 */
		
		int offset = 7;

		if (pn532_packetbuffer[offset + 0] != 1) {
			return -1;
		}
		// int sens_res = pn532_packetbuffer[2];
		// sens_res <<= 8;
		// sens_res |= pn532_packetbuffer[3];

		// DMSG("ATQA: 0x"); DMSG_HEX(sens_res);
		// DMSG("SAK: 0x"); DMSG_HEX(pn532_packetbuffer[4]);
		// DMSG("\n");

		/* Card appears to be Mifare Classic */
		int uidLength = pn532_packetbuffer[offset + 5];

		for (int i = 0; i < uidLength; i++) {
			buffer[i] = pn532_packetbuffer[offset + 6 + i];
		}

		return uidLength;
	}

}