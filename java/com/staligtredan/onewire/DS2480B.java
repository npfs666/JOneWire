package com.staligtredan.onewire;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.fazecast.jSerialComm.SerialPort;

public class DS2480B {

	private static SerialPort comPort;

	private final static byte commandReset = (byte) 0xC5;
	private final static byte commandResetResponseShorted = (byte) 0xCC;
	private final static byte commandResetResponsePresencePulse = (byte) 0xCD;
	private final static byte commandResetResponseAlarmingPresencePulse = (byte) 0xCE;
	private final static byte commandResetResponseNoPresencePulse = (byte) 0xCF;
	private final static byte[] hardReset = { (byte) 0xC1, (byte) 0xE3, (byte) 0xE3, commandReset };

	private final static byte[] hexFF = { (byte) 0xFF };

	public final static byte[] commandPullupDuration131ms = { (byte) 0x35 };
	public final static byte[] commandPullupDuration524ms = { (byte) 0x39 };
	public final static byte[] commandPullupDuration1048ms = { (byte) 0x3B };

	private final static byte[] commandTerminatePulse = { (byte) 0xF1 };

	private final static byte[] commandArmStrongPullupTrue = { (byte) 0xEF };
	private final static byte[] commandArmStrongPullupFalse = { (byte) 0xED };

	private final static byte[] commandDataMode = { (byte) 0xE1 };
	private final static byte[] commandCommandMode = { (byte) 0xE3 };

	private final static Level lowLevelLogs = Level.FINE;


	/**
	 * Opens a UART link with the DS2480
	 * 
	 * @param portDescriptor port name '/dev/tty*' on linux
	 * @param baudRate
	 */
	public static void openPort( String portDescriptor, int baudRate ) {


		comPort = SerialPort.getCommPort(portDescriptor);
		
		for (SerialPort s : SerialPort.getCommPorts() ) {
			System.out.println(s.getDescriptivePortName());
		}

		comPort.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
		comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING,50, 50);
		
		if( comPort.openPort() ) {
			log(Level.INFO,
					"Serial port [" + portDescriptor + "] opened @ " + 9600 + "bauds, 8 bits, 1 stop bit, no parity.");
		} else {
			log(Level.WARNING, "Can not open the serial port [" + portDescriptor + "]");
		}

		reset();
	}

	
	public static void close() {
		
		if( (comPort != null) && comPort.isOpen() ) {
			if( comPort.closePort() )
				log(Level.INFO, "Serial port closed with success");
			else 
				log(Level.INFO, "Serial port couldn't close");
		} else {
			log(Level.INFO, "Serial port was aldready closed");
		}
	}
	
	public static boolean isOpen() {
		return comPort.isOpen();
	}
	
	
	private static void log(Level level, String msg) {
		
		Logger.getLogger(DS2480B.class.getName()).log(level, msg);
	}
	

	/**
	 * Empties the serial reading buffer
	 */
	private static void flush() {
		
		
		/**
		 * Le problème de vitesse du système viens d'ici
		 * 
		 * on peut pas regarder si ya des données en attente avant flush car le cpu va trop vite 
		 * par rapport au port série.
		 * Avec une pause de 10ms ça passe mais on ralenti forcément le système (249ms pour convert&read 1 sonde)
		 * 
		 * Sinon faudrait forceRead 1 byte ou 2 à chaque flush en non blocking (2500ms)
		 * c'est de la merde comme avant
		 */
		
		/*try {
			Thread.sleep(10);
		} catch ( InterruptedException e ) {
			e.printStackTrace();
		}*/
		//if( comPort.bytesAvailable() > 0 ) {
			
			byte[] readBuffer = new byte[comPort.bytesAvailable()];
			
			comPort.readBytes(readBuffer, comPort.bytesAvailable());
		//}
		

		// Ancien code
		/*comPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0,0);
		byte[] readBuffer = new byte[1];
		comPort.readBytes(readBuffer, readBuffer.length);
		comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);*/
	}



	/**
	 * Hexadecimal string representation of a byte
	 * 
	 * @param value
	 * @return
	 */
	public static String hexFormat( byte value ) {
		return Integer.toHexString(Byte.toUnsignedInt(value));
	}



	/**
	 * Send a 1-Wire reset signal
	 * 
	 * @return
	 */
	public static boolean reset() {

		byte[] writeBuffer = new byte[1];
		byte[] readBuffer = new byte[1];

		flush();

		writeBuffer[0] = commandReset;
		comPort.writeBytes(writeBuffer, 1);
		
		int numRead = comPort.readBytes(readBuffer, readBuffer.length);

		if( numRead > 0 ) {

			if( Byte.compare(readBuffer[0], commandResetResponseShorted) == 0 ) {
				log(Level.INFO,"DS2480 : Reset response : 1-Wire is shorted");
			} else if( Byte.compare(readBuffer[0], commandResetResponsePresencePulse) == 0 ) {
				log(Level.FINE,"DS2480 : Reset response : Presence pulse, ok");
			} else if( Byte.compare(readBuffer[0], commandResetResponseAlarmingPresencePulse) == 0 ) {
				log(Level.INFO,"DS2480 : Reset response : Alarming presence pulse");
			} else if( Byte.compare(readBuffer[0], commandResetResponseNoPresencePulse) == 0 ) {
				log(Level.INFO,"DS2480 : Reset response : No presence pulse, network empty");
			}
			// If bad response, try resync -> hard reset
			else {
				log(Level.INFO,"DS2480 : Reset response : bad response : trying hardreset 2");
				comPort.writeBytes(hardReset, hardReset.length);
				flush();
				//reset();
			}

			return true;

		} else {

			//if( reset() )
			//	log(Level.INFO,"First reset after DS2480 bootup, ok");
			//else
				log(Level.INFO,"Error reading from Serial port, port is probably closed");
			return false;
		}
	}



	/**
	 * Retrieves result.length data on the 1-Wire
	 * 
	 * @param result
	 * 
	 * @return true if successful
	 */
	public static boolean waitData( byte[] result ) {

		byte[] readBuffer = new byte[1];
		int count = 0;

		flush();

		for ( int i = 0; i < result.length; i++ ) {

			comPort.writeBytes(hexFF, hexFF.length);

			int numRead = comPort.readBytes(readBuffer, 1);

			if( numRead > 0 ) {
				result[i] = readBuffer[0];
				count++;
				log(lowLevelLogs, "DS2480 : wait data response : ok : buffer[" + i + "] = " + hexFormat(readBuffer[0]));
			} else {
				log(Level.WARNING, "DS2480 : wait data response : Error during wait data");
				return false;
			}
		}

		if( count == result.length ) {
			log(lowLevelLogs, "DS2480 : data response : complete : "+result.length+" byte read");
			return true;
		} else {
			log(Level.WARNING, "DS2480 : data response : incomplete : "+count+"/"+result.length+" byte read");
			return false;
		}
	}



	/**
	 * Sets the duration of the pullup
	 * 
	 * @return true if successful
	 */
	public static boolean setPullupDuration( byte[] commandPullupDuration ) {

		byte[] readBuffer = new byte[1];

		flush();

		comPort.writeBytes(commandPullupDuration, commandPullupDuration.length);

		int numRead = comPort.readBytes(readBuffer, readBuffer.length);

		if( numRead > 0 ) {

			if( Byte.compare((byte) (commandPullupDuration[0] & 0xFE), readBuffer[0]) == 0 ) {

				log(lowLevelLogs,"DS2480 : pullup duration response : ok : " + hexFormat(readBuffer[0]));
				return true;
			} else {
				log(Level.WARNING,"DS2480 : pullup duration response : bad : " + hexFormat(readBuffer[0]));
				return false;
			}
		}

		return false;
	}



	/**
	 * Waits a waitPulse on the 1-Wire network
	 * 
	 * @return true if successful
	 */
	public static boolean waitPulse() {

		byte[] readBuffer = new byte[1];

		int numRead = comPort.readBytes(readBuffer, readBuffer.length);

		if( numRead > 0 ) {

			if( Byte.compare((byte) (readBuffer[0] & 0xF0), (byte) 0x70) == 0 ) {

				log(lowLevelLogs,"DS2480 : wait pulse response : ok : " + hexFormat(readBuffer[0]));
				return true;
			} else {

				log(Level.WARNING,"DS2480 : wait pulse response : bad : " + hexFormat(readBuffer[0]));
				return false;
			}

		}

		return false;
	}



	/**
	 * Sends a terminate pulse on the 1-Wire
	 * 
	 * @return true if successful
	 */
	public static boolean terminatePulse() {

		byte[] readBuffer = new byte[1];

		flush();

		comPort.writeBytes(commandTerminatePulse, commandTerminatePulse.length);

		int numRead = comPort.readBytes(readBuffer, readBuffer.length);

		if( numRead > 0 ) {

			if( Byte.compare((byte) (readBuffer[0] & 0xE0), (byte) 0xE0) == 0 ) {

				log(lowLevelLogs,"DS2480 : terminate pulse response : ok : " + hexFormat(readBuffer[0]));

				return true;
			} else {

				log(Level.WARNING,"DS2480 : terminate pulse response : bad : " + hexFormat(readBuffer[0]));

				return false;
			}

		}

		return false;
	}



	/**
	 * Sets the strong pullup or not on the 1-Wire network
	 * 
	 * @param state
	 * 
	 * @return true if successful
	 */
	public static boolean armStrongPullup( boolean state ) {

		byte[] bb = commandArmStrongPullupFalse;

		flush();

		if( state )
			bb = commandArmStrongPullupTrue;

		comPort.writeBytes(bb, 1);

		return true;
	}



	/**
	 * Sends data on the 1-Wire network
	 * 
	 * @param data
	 * @return true if successful
	 */
	public static boolean sendData( byte data ) {

		byte[] buffer = new byte[1];

		flush();

		buffer[0] = data;
		comPort.writeBytes(buffer, 1);

		int numRead = comPort.readBytes(buffer, buffer.length);

		if( numRead > 0 ) {

			if( Byte.compare(buffer[0], data) == 0 ) {

				log(lowLevelLogs,"DS2480 : data response : ok : data " + hexFormat(data) + " result " + hexFormat(buffer[0]));

				return true;
			} else {

				log(Level.WARNING,"DS2480 : data response : bad : data " + hexFormat(data) + " result " + hexFormat(buffer[0]));
				return false;
			}
		}

		return false;
	}



	/**
	 * Set the DS2480 in data mode
	 * 
	 * @return true if successful
	 */
	public static boolean setDataMode() {

		flush();

		comPort.writeBytes(commandDataMode, 1);

		return true;
	}



	/**
	 * Set the DS2480 in command mode
	 * 
	 * @return true if successful
	 */
	public static boolean setCommandMode() {

		flush();

		comPort.writeBytes(commandCommandMode, 1);

		return true;
	}
	
	public static void readRom() {
		
		//flush();
		DS2480B.reset();
		DS2480B.setDataMode();
		DS2480B.sendData((byte) 0x33);
		
		byte[] adress = new byte[8];
		waitData(adress);
		DS2480B.setDataMode();
		DS2480B.reset();
		
		for( byte b : adress ) {

			System.out.println(Integer.toHexString(b));
		}
	}

}
