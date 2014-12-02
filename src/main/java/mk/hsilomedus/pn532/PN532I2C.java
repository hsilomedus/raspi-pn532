
package mk.hsilomedus.pn532;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.wiringpi.Gpio;

public class PN532I2C implements IPN532Interface {

  private I2CBus i2cBus;
  private I2CDevice i2cDevice;
  boolean debug = false;
  boolean debugReads = false;

  private byte command;

  private static final int DEVICE_ADDRESS = 0x24;

  @Override
  public void begin() {
    try {
      i2cBus = I2CFactory.getInstance(I2CBus.BUS_1);
      log("Connected to bus OK!!!");

      i2cDevice = i2cBus.getDevice(DEVICE_ADDRESS);
      log("Connected to device OK!!!");

      Thread.sleep(500);

    } catch (IOException e) {
      System.out.println("Exception: " + e.getMessage());
    } catch (InterruptedException e) {
      System.out.println("Interrupted Exception: " + e.getMessage());
    }

  }

  @Override
  public void wakeup() {

  }

  @Override
  public CommandStatus writeCommand(byte[] header, byte[] body) throws InterruptedException {
    log("pn532i2c.writeCommand(header:" + getByteString(header) + ", body: " + getByteString(body) + ")");

    List<Byte> toSend = new ArrayList<Byte>();

    command = header[0];
    try {
      toSend.add(PN532_PREAMBLE);
      toSend.add(PN532_STARTCODE1);
      toSend.add(PN532_STARTCODE2);

      byte cmd_len = (byte) header.length;
      cmd_len += (byte) body.length;
      cmd_len++;
      byte cmdlen_1 = (byte) (~cmd_len + 1);

      toSend.add(cmd_len);
      toSend.add(cmdlen_1);

      toSend.add(PN532_HOSTTOPN532);

      byte sum = PN532_HOSTTOPN532;

      for (int i = 0; i < header.length; i++) {
        toSend.add(header[i]);
        sum += header[i];
      }

      for (int i = 0; i < body.length; i++) {
        toSend.add(body[i]);
        sum += body[i];
      }

      byte checksum = (byte) (~sum + 1);
      toSend.add(checksum);
      toSend.add(PN532_POSTAMBLE);
      byte[] bytesToSend = new byte[toSend.size()];
      for (int i = 0; i < bytesToSend.length; i++) {
        bytesToSend[i] = toSend.get(i);
      }
      log("pn532i2c.writeCommand sending " + getByteString(bytesToSend));
      i2cDevice.write(DEVICE_ADDRESS, bytesToSend, 0, bytesToSend.length);

    } catch (IOException e) {
      System.out.println("pn532i2c.writeCommand exception occured: " + e.getMessage());
      return CommandStatus.INVALID_ACK;
    }
    log("pn532i2c.writeCommand transferring to waitForAck())");
    return waitForAck(5000);

  }

  private CommandStatus waitForAck(int timeout) throws InterruptedException {
    log("pn532i2c.waitForAck()");

    byte ackbuff[] = new byte[7];
    byte PN532_ACK[] = new byte[]{0, 0, (byte) 0xFF, 0, (byte) 0xFF, 0};

    int timer = 0;
    String message = "";
    while (true) {
      try {
        int read = i2cDevice.read(ackbuff, 0, 7);
        if (debugReads && read > 0) {
          log("pn532i2c.waitForAck Read " + read + " bytes.");
        }
      } catch (IOException e) {
        message = e.getMessage();
      }

      if ((ackbuff[0] & 1) > 0) {
        break;
      }

      if (timeout != 0) {
        timer += 10;
        if (timer > timeout) {
          log("pn532i2c.waitForAck timeout occured: " + message);
          return CommandStatus.TIMEOUT;
        }
      }
      Gpio.delay(10);

    }

    for (int i = 1; i < ackbuff.length; i++) {
      if (ackbuff[i] != PN532_ACK[i - 1]) {
        log("pn532i2c.waitForAck Invalid Ack.");
        return CommandStatus.INVALID_ACK;
      }
    }
    log("pn532i2c.waitForAck OK");
    return CommandStatus.OK;

  }

  @Override
  public CommandStatus writeCommand(byte[] header) throws InterruptedException {
    return writeCommand(header, new byte[0]);
  }

  @Override
  public int readResponse(byte[] buffer, int expectedLength, int timeout) throws InterruptedException {
    log("pn532i2c.readResponse");

    byte response[] = new byte[expectedLength + 2];

    int timer = 0;

    while (true) {
      try {
        int read = i2cDevice.read(response, 0, expectedLength + 2);
        if (debugReads && read > 0) {
          log("pn532i2c.waitForAck Read " + read + " bytes.");
        }
      } catch (IOException e) {
        // Nothing, timeout will occur if an error has happened.
      }

      if ((response[0] & 1) > 0) {
        break;
      }

      if (timeout != 0) {
        timer += 10;
        if (timer > timeout) {
          log("pn532i2c.readResponse timeout occured.");
          return -1;
        }
      }
      Gpio.delay(10);

    }

    int ind = 1;

    if (PN532_PREAMBLE != response[ind++] || PN532_STARTCODE1 != response[ind++] || PN532_STARTCODE2 != response[ind++]) {
      log("pn532i2c.readResponse bad starting bytes found");
      return -1;
    }

    byte length = response[ind++];
    byte com_length = length;
    com_length += response[ind++];
    if (com_length != 0) {
      log("pn532i2c.readResponse bad length checksum");
      return -1;
    }

    byte cmd = 1;
    cmd += command;

    if (PN532_PN532TOHOST != response[ind++] || (cmd) != response[ind++]) {
      log("pn532i2c.readResponse bad command check.");
      return -1;
    }

    length -= 2;
    if (length > expectedLength) {
      log("pn532i2c.readResponse not enough space");
      return -1;
    }

    byte sum = PN532_PN532TOHOST;
    sum += cmd;

    for (int i = 0; i < length; i++) {
      buffer[i] = response[ind++];
      sum += buffer[i];
    }

    byte checksum = response[ind++];
    checksum += sum;
    if (0 != checksum) {
      log("pn532i2c.readResponse bad checksum");
      return -1;
    }

    return length;

  }

  @Override
  public int readResponse(byte[] buffer, int expectedLength) throws InterruptedException {
    return readResponse(buffer, expectedLength, 1000);
  }


  private String getByteString(byte[] arr) {
    String output = "[";

    if (arr != null) {
      for (int i = 0; i < arr.length; i++) {
        output += Integer.toHexString(arr[i]) + " ";
      }
    }
    return output.trim() + "]";
  }
  
  private void log(String message) {
    if (debug) {
      System.out.println(message);
    }
  }

}
