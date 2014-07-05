package mk.hsilomedus.pn532;

public interface IPN532Interface {

	public abstract void begin();

	public abstract void wakeup();

	public abstract CommandStatus writeCommand(byte[] header, byte[] body)
			throws InterruptedException;

	public abstract CommandStatus writeCommand(byte header[]) throws InterruptedException;

	public abstract int readResponse(byte[] buffer, int expectedLength,
			int timeout) throws InterruptedException;

	public abstract int readResponse(byte[] buffer, int expectedLength)
			throws InterruptedException;

}