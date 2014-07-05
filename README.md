pn532 NFC with Java8, pi4j and Raspberry Pi

Used device: ITEAD PN532 NFC Module: http://imall.iteadstudio.com/prototyping/basic-module/im130625002.html
Code Based on: 
 * http://blog.iteadstudio.com/to-drive-itead-pn532-nfc-module-with-raspberry-pi/
 * https://github.com/elechouse/PN532

For SPI enabling, follow the instructions on the ITEAD blog post:

"
First, before installing the library offered by us, we need to modify some of the configurations of Raspberry Pie to make SPI module automatically activated when powering on:

cd / etc / modprobe.d /

Enter the Configuration folder

sudo nano raspi-blacklist.conf

Open the configuration file as super user

blachlist sip-bcm2708

Comment out that line, to achieve SPI module loading when powering on.
" 
