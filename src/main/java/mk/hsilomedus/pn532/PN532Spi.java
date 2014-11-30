package mk.hsilomedus.pn532;

import com.pi4j.wiringpi.Gpio;
import com.pi4j.wiringpi.Spi;

public class PN532Spi implements IPN532Interface {

	static final int SPICHANNEL = 1;
	static final int SPISPEED = 1000000;

	static final byte PN532_SPI_READY = 0x01;
	static final byte PN532_SPI_STATREAD = 0x02;
	static final byte PN532_SPI_DATAWRITE = 0x01;
	static final byte PN532_SPI_DATAREAD = 0x03;

	static final byte PN532_PREAMBLE = 0x00;
	static final byte PN532_STARTCODE1 = 0x00;
	static final byte PN532_STARTCODE2 = (byte) 0xFF;
	static final byte PN532_POSTAMBLE = 0x00;
	static final byte PN532_HOSTTOPN532 = (byte) 0xD4;

	static final int OUTPUT = 1;

	static final int LOW = 0;
	static final int HIGH = 1;

	static final int _cs = 10;
	static final int rst = 0;

	@Override
	public void begin() {
		System.out.println("Beginning SPI.");

		int j = Gpio.wiringPiSetup();
		System.out.println("Wiringpisetup is " + j);
		int fd = Spi.wiringPiSPISetup(SPICHANNEL, SPISPEED);
		System.out.println("Wiringpispisetup is " + fd);

		if (fd <= -1) {
			System.out.println(" ==>> SPI SETUP FAILED");
			throw new RuntimeException("ERROR!");
		}
		Gpio.pinMode(_cs, OUTPUT);

	}

	@Override
	public void wakeup() {
		System.out.println("Waking SPI.");
		Gpio.digitalWrite(_cs, HIGH);
		Gpio.digitalWrite(rst, HIGH);
		Gpio.digitalWrite(_cs, LOW);
	}

	@Override
	public CommandStatus writeCommand(byte[] header, byte[] body)
			throws InterruptedException {

		System.out.println("Medium.writeCommand(" + getByteString(header) + " "
				+ (body != null ? body : "") + ")");
		byte checksum;
		byte cmdlen_1;
		byte i;
		byte checksum_1;

		byte cmd_len = (byte) header.length;

		cmd_len++;

		Gpio.digitalWrite(_cs, LOW);
		Gpio.delay(2); // or whatever the delay is for waking up the board

		writeByte(PN532_SPI_DATAWRITE); // 0x01

		checksum = PN532_PREAMBLE + PN532_PREAMBLE + PN532_STARTCODE2;
		writeByte(PN532_PREAMBLE); // 0x00
		writeByte(PN532_PREAMBLE); // 0x00
		writeByte(PN532_STARTCODE2); // 0xff

		writeByte(cmd_len); // 0x02
		cmdlen_1 = (byte) (~cmd_len + 1);
		writeByte(cmdlen_1); // 0x01

		writeByte(PN532_HOSTTOPN532); // 0xd4
		checksum += PN532_HOSTTOPN532;

		for (i = 0; i < cmd_len - 1; i++) {
			writeByte(header[i]);
			checksum += header[i];
		}

		checksum_1 = (byte) ~checksum;
		writeByte(checksum_1);
		writeByte(PN532_POSTAMBLE);
		Gpio.digitalWrite(_cs, HIGH);

		return waitForAck(1000);
	}

	@Override
	public CommandStatus writeCommand(byte[] header)
			throws InterruptedException {
		return writeCommand(header, null);
	}

	@Override
	public int readResponse(byte[] buffer, int expectedLength, int timeout)
			throws InterruptedException {
		System.out.println("Medium.readResponse(..., " + expectedLength + ", "
				+ timeout + ")");
		byte i;

		Gpio.digitalWrite(_cs, LOW);
		Gpio.delay(2);
		writeByte(PN532_SPI_DATAREAD);

		for (i = 0; i < expectedLength; i++) {
			Gpio.delay(1);
			buffer[i] = readByte();
		}
		Gpio.digitalWrite(_cs, HIGH);
		
		return 1;
	}

	@Override
	public int readResponse(byte[] buffer, int expectedLength)
			throws InterruptedException {
		return readResponse(buffer, expectedLength, 1000);
	}

	private CommandStatus waitForAck(int timeout) throws InterruptedException {
		System.out.println("Medium.waitForAck()");

		int timer = 0;
		while (readSpiStatus() != PN532_SPI_READY) {
			if (timeout != 0) {
				timer += 10;
				if (timer > timeout) {
					return CommandStatus.TIMEOUT;
				}
			}
			Gpio.delay(10);
		}
		if (!checkSpiAck()) {
			return CommandStatus.INVALID_ACK;
		}

		timer = 0;
		while (readSpiStatus() != PN532_SPI_READY) {
			if (timeout != 0) {
				timer += 10;
				if (timer > timeout) {
					return CommandStatus.TIMEOUT;
				}
			}
			Gpio.delay(10);
		}
		return CommandStatus.OK; // ack'd command
	}
	
	@Override
	public int getOffsetBytes() {
	  return 7;
	}

	private byte readSpiStatus() throws InterruptedException {
		// System.out.println("Medium.readSpiStatus()");
		byte status;

		Gpio.digitalWrite(_cs, LOW);
		Gpio.delay(2);
		writeByte(PN532_SPI_STATREAD);
		status = readByte();
		Gpio.digitalWrite(_cs, HIGH);
		return status;
	}

	boolean checkSpiAck() throws InterruptedException {
		System.out.println("Medium.checkSpiAck()");
		byte ackbuff[] = new byte[6];
		byte PN532_ACK[] = new byte[] { 0, 0, (byte) 0xFF, 0, (byte) 0xFF, 0 };

		readResponse(ackbuff, 6);
		for (int i = 0; i < ackbuff.length; i++) {
			if (ackbuff[i] != PN532_ACK[i]) {
				return false;
			}
		}
		return true;
	}

	void writeByte(byte byteToWrite) {
		// System.out.println("Medium.write(" + Integer.toHexString(_data) +
		// ")");
		byte[] dataToSend = new byte[1];
		dataToSend[0] = reverseByte(byteToWrite);
		Spi.wiringPiSPIDataRW(SPICHANNEL, dataToSend, 1);
	}

	byte readByte() {
		byte[] data = new byte[1];
		data[0] = 0;
		Spi.wiringPiSPIDataRW(SPICHANNEL, data, 1);
		data[0] = reverseByte(data[0]);
		// System.out.println("Medium.readF() = " +
		// Integer.toHexString(data[0]));
		return data[0];
	}

	private String getByteString(byte[] arr) {
		String output = "[";
		for (int i = 0; i < arr.length; i++) {
			output += Integer.toHexString(arr[i]) + " ";
		}
		return output.trim() + "]";
	}

	byte reverseByte(byte inputByte) {
	  byte input = inputByte;
		byte output = 0;
		for (int p = 0; p < 8; p++) {
			if ((input & 0x01) > 0) {
				output |= 1 << (7 - p);
			}
			input = (byte) (input >> 1);
		}
		return output;
	}
	

}