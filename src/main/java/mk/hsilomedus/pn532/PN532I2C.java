/*
 * Copyright (C) 2014 by Netcetera AG.
 * All rights reserved.
 *
 * The copyright to the computer program(s) herein is the property of Netcetera AG, Switzerland.
 * The program(s) may be used and/or copied only with the written permission of Netcetera AG or
 * in accordance with the terms and conditions stipulated in the agreement/contract under which 
 * the program(s) have been supplied.
 */
package mk.hsilomedus.pn532;

import java.io.IOException;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.wiringpi.Gpio;


public class PN532I2C implements IPN532Interface {
  
  static final byte PN532_PREAMBLE = 0x00;
  static final byte PN532_STARTCODE1 = 0x00;
  static final byte PN532_STARTCODE2 = (byte) 0xFF;
  static final byte PN532_POSTAMBLE = 0x00;
  
  static final byte PN532_HOSTTOPN532 = (byte) 0xD4;
  static final byte PN532_PN532TOHOST = (byte) 0xD5;
  
  I2CBus i2cBus;
  I2CDevice i2cDevice;
  
  byte command;
  
  private static final int DEVICE_ADDRESS = 0x24;

  @Override
  public void begin() {
    // TODO Auto-generated method stub
    try {
      i2cBus = I2CFactory.getInstance(I2CBus.BUS_1);
      System.out.println("Connected to bus OK!!!");
      
      i2cDevice = i2cBus.getDevice(DEVICE_ADDRESS);
      System.out.println("Connected to device OK!!!");
      
      Thread.sleep(500);

      
    } catch (IOException e) {
      System.out.println("Exception: " + e.getMessage());
    } catch (InterruptedException e) {
        System.out.println("Interrupted Exception: " + e.getMessage());
    }
    

  }

  @Override
  public void wakeup() {
    // TODO Auto-generated method stub

  }

  @Override
  public CommandStatus writeCommand(byte[] header, byte[] body) throws InterruptedException {
    
    command = header[0];
    try {
      i2cDevice.write(PN532_PREAMBLE);    
      i2cDevice.write(PN532_STARTCODE1);
      i2cDevice.write(PN532_STARTCODE2);
      
      byte cmd_len = (byte) header.length;
      cmd_len += (byte) body.length;
      cmd_len++;
      byte cmdlen_1 = (byte) (~cmd_len + 1);
      
      i2cDevice.write(cmd_len); 
      i2cDevice.write(cmdlen_1); 
      
      byte sum = 0;
      
      for (int i = 0; i < header.length; i++) {
        i2cDevice.write(header[i]);
        sum += header[i];
      }
      
      for (int i = 0; i < body.length; i++) {
        i2cDevice.write(body[i]);
        sum += body[i];
      }
      
      byte checksum = (byte) (~sum + 1);
      i2cDevice.write(checksum);
      i2cDevice.write(PN532_POSTAMBLE);
    
    } catch (IOException e) {
      return CommandStatus.INVALID_ACK;
    }
    
    return waitForAck(1000);
    
  }
  
  private CommandStatus waitForAck(int timeout) throws InterruptedException {
    
    byte ackbuff[] = new byte[7];
    byte PN532_ACK[] = new byte[] { 0, 0, (byte) 0xFF, 0, (byte) 0xFF, 0 };

    int timer = 0;
    
    while (true) {
      try {
        i2cDevice.read(ackbuff, 0, 7);
      } catch (IOException e) {
        // Nothing, timeout will occur if an error has happened.
      }
      
      if ((ackbuff[0] & 1) > 0) {
        break;
      }
      
      if (timeout != 0) {
        timer += 10;
        if (timer > timeout) {
          return CommandStatus.TIMEOUT;
        }
      }
      Gpio.delay(10);
    
    }
    
    for (int i = 1; i < ackbuff.length; i++) {
      if (ackbuff[i] != PN532_ACK[i-1]) {
        return CommandStatus.INVALID_ACK;
      }
    }
    return CommandStatus.OK;
    
  }

  @Override
  public CommandStatus writeCommand(byte[] header) throws InterruptedException {
    return writeCommand(header, null);
  }

  @Override
  public int readResponse(byte[] buffer, int expectedLength, int timeout) throws InterruptedException {
    
    byte response[] = new byte[expectedLength + 2];

    int timer = 0;
    
    while (true) {
      try {
        i2cDevice.read(response, 0, expectedLength + 2);
      } catch (IOException e) {
        // Nothing, timeout will occur if an error has happened.
      }
      
      if ((response[0] & 1) > 0) {
        break;
      }
      
      if (timeout != 0) {
        timer += 10;
        if (timer > timeout) {
          return -1;
        }
      }
      Gpio.delay(10);
    
    }
    
    int ind=1;
    
    if (PN532_PREAMBLE != response[ind++]      ||       // PREAMBLE
        PN532_STARTCODE1 != response[ind++]  ||       // STARTCODE1
        PN532_STARTCODE2 != response[ind++]           // STARTCODE2
        ) {
      return -1;
    }
    
    byte length = response[ind++];
    byte com_length = length;
    com_length += response[ind++];
    if (com_length != 0) {
      return -1;
    }
    
    byte cmd = 1;
    cmd += command;
    
    if (PN532_PN532TOHOST != response[ind++] || (cmd) != response[ind++]) {
      return -1;
  }
  
  length -= 2;
  if (length > expectedLength) {
      return -1;  // not enough space
  }
  
//  DMSG("read:  ");
//  DMSG_HEX(cmd);
  
  byte sum = PN532_PN532TOHOST;
  sum += cmd;
  
  for (int i = 0; i < length; i++) {
      buffer[i] = response[ind++];
      sum += buffer[i];
      
//      DMSG_HEX(buf[i]);
  }
//  DMSG('\n');
  
  byte checksum = response[ind++];
  checksum += sum;
  if (0 != checksum) {
//      DMSG("checksum is not ok\n");
//      return PN532_INVALID_FRAME;
    return -1;
  }
//  read();         // POSTAMBLE
  
  return length;
    
  }

  @Override
  public int readResponse(byte[] buffer, int expectedLength) throws InterruptedException {
    return readResponse(buffer, expectedLength, 1000);
  }

}
