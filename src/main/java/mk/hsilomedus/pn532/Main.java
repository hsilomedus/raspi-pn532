package mk.hsilomedus.pn532;
public class Main {

	static final byte PN532_MIFARE_ISO14443A = 0x00;

	public static void main(String[] args) throws InterruptedException {
		IPN532Interface pn532Interface = new PN532Spi();
		PN532 nfc = new PN532(pn532Interface);

		// Start
		System.out.println("Starting up...");
		nfc.begin();
		Thread.sleep(1000);

		long versiondata = nfc.getFirmwareVersion();
		if (versiondata == 0) {
			System.out.println("Didn't find PN53x board");
			return;
		}
		// Got ok data, print it out!
		System.out.print("Found chip PN5");
		System.out.println(Long.toHexString((versiondata >> 24) & 0xFF));

		System.out.print("Firmware ver. ");
		System.out.print(Long.toHexString((versiondata >> 16) & 0xFF));
		System.out.print('.');
		System.out.println(Long.toHexString((versiondata >> 8) & 0xFF));

		// configure board to read RFID tags
		nfc.SAMConfig();

		System.out.println("Waiting for an ISO14443A Card ...");

		byte[] buffer = new byte[8];
		while (true) {
			int readLength = nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A,
					buffer);

			if (readLength > 0) {
				System.out.println("Found an ISO14443A card");

				System.out.print("  UID Length: ");
				System.out.print(readLength);
				System.out.println(" bytes");

				System.out.print("  UID Value: [");
				for (int i = 0; i < readLength; i++) {
					System.out.print(Integer.toHexString(buffer[i]));
				}
				System.out.println("]");
			}

			Thread.sleep(100);
		}

	}
}