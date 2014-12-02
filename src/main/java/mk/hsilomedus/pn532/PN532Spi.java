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
	
	static final int OUTPUT = 1;

	static final int LOW = 0;
	static final int HIGH = 1;

	static final int _cs = 10;
	static final int rst = 0;
	
	private byte command;
	
	private boolean debug = false;

	@Override
	public void begin() {
		log("Beginning SPI.");

		int j = Gpio.wiringPiSetup();
		log("Wiringpisetup is " + j);
		int fd = Spi.wiringPiSPISetup(SPICHANNEL, SPISPEED);
		log("Wiringpispisetup is " + fd);

		if (fd <= -1) {
			log("SPI Setup failed!");
			throw new RuntimeException("SPI Setup failed!");
		}
		Gpio.pinMode(_cs, OUTPUT);

	}

	@Override
	public void wakeup() {
		log("Waking SPI.");
		Gpio.digitalWrite(_cs, HIGH);
		Gpio.digitalWrite(rst, HIGH);
		Gpio.digitalWrite(_cs, LOW);
	}

	@Override
	public CommandStatus writeCommand(byte[] header, byte[] body)
			throws InterruptedException {

		log("Medium.writeCommand(" + getByteString(header) + " "
				+ (body != null ? getByteString(body) : "") + ")");
		
		command = header[0];
		
		byte checksum;
		byte cmdlen_1;
		byte i;
		byte checksum_1;

		byte cmd_len = (byte) header.length;

		cmd_len++;

		Gpio.digitalWrite(_cs, LOW);
		Gpio.delay(2); 

		writeByte(PN532_SPI_DATAWRITE); 

		checksum = PN532_PREAMBLE + PN532_STARTCODE1 + PN532_STARTCODE2;
		writeByte(PN532_PREAMBLE); 
		writeByte(PN532_STARTCODE1); 
		writeByte(PN532_STARTCODE2); 

		writeByte(cmd_len); 
		cmdlen_1 = (byte) (~cmd_len + 1);
		writeByte(cmdlen_1); 

		writeByte(PN532_HOSTTOPN532); 
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
		log("Medium.readResponse(..., " + expectedLength + ", "
				+ timeout + ")");

		Gpio.digitalWrite(_cs, LOW);
		Gpio.delay(2);
		writeByte(PN532_SPI_DATAREAD);
		
		if (PN532_PREAMBLE != readByte() || PN532_STARTCODE1 != readByte() || PN532_STARTCODE2 != readByte()) {
      log("pn532i2c.readResponse bad starting bytes found");
      return -1;
    }

    byte length = readByte();
    byte com_length = length;
    com_length += readByte();
    if (com_length != 0) {
      log("pn532i2c.readResponse bad length checksum");
      return -1;
    }

    byte cmd = 1;
    cmd += command;

    if (PN532_PN532TOHOST != readByte() || (cmd) != readByte()) {
      log("pn532i2c.readResponse bad command check.");
      return -1;
    }

    length -= 2;
    if (length > expectedLength) {
      log("pn532i2c.readResponse not enough space");
      readByte();
      readByte();
      return -1;
    }

    byte sum = PN532_PN532TOHOST;
    sum += cmd;

    for (int i = 0; i < length; i++) {
      buffer[i] = readByte();
      sum += buffer[i];
    }

    byte checksum = readByte();
    checksum += sum;
    if (0 != checksum) {
      log("pn532i2c.readResponse bad checksum");
      return -1;
    }
    
    readByte(); //POSTAMBLE
    
		Gpio.digitalWrite(_cs, HIGH);
		
		return length;
	}

	@Override
	public int readResponse(byte[] buffer, int expectedLength)
			throws InterruptedException {
		return readResponse(buffer, expectedLength, 1000);
	}

	private CommandStatus waitForAck(int timeout) throws InterruptedException {
		log("Medium.waitForAck()");

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
		return CommandStatus.OK; 
	}
//	
//	@Override
//	public int getOffsetBytes() {
//	  return 7;
//	}

	private byte readSpiStatus() throws InterruptedException {
		log("Medium.readSpiStatus()");
		byte status;

		Gpio.digitalWrite(_cs, LOW);
		Gpio.delay(2);
		writeByte(PN532_SPI_STATREAD);
		status = readByte();
		Gpio.digitalWrite(_cs, HIGH);
		return status;
	}

	private boolean checkSpiAck() throws InterruptedException {
		log("Medium.checkSpiAck()");
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

	private void writeByte(byte byteToWrite) {
		// System.out.println("Medium.write(" + Integer.toHexString(_data) +
		// ")");
		byte[] dataToSend = new byte[1];
		dataToSend[0] = reverseByte(byteToWrite);
		Spi.wiringPiSPIDataRW(SPICHANNEL, dataToSend, 1);
	}

	private byte readByte() {
	  Gpio.delay(1);
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

	private byte reverseByte(byte inputByte) {
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
	
	private void log(String message) {
    if (debug) {
      System.out.println(message);
    }
  }
	

}