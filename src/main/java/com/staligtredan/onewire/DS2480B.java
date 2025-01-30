package com.staligtredan.onewire;

import java.util.ArrayList;
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
	
	private final static byte[] commandSearchAcceleratorOn = { (byte) 0xB1 };
	private final static byte[] commandSearchAcceleratorOff = { (byte) 0xA1};
	
	public final static byte commandSearchRom= (byte) 0xF0;

	private final static Level lowLevelLogs = Level.FINE;

	public static ArrayList<byte[]> OWList = new ArrayList<>();


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

	public static boolean setSearchAcceleratorOn() {

		flush();

		comPort.writeBytes(commandSearchAcceleratorOn, 1);

		return true;
	}

	public static boolean setSearchAcceleratorOff() {

		flush();

		comPort.writeBytes(commandSearchAcceleratorOff, 1);

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
		DS2480B.setCommandMode();
		DS2480B.reset();
		
		for( byte b : adress ) {

			System.out.println(Integer.toHexString(b));
		}
		
		
	}
	
	
	
	public static void searchROMs()
	{
	  int loop;
	  byte[] TData = new byte[16];
	  byte[] RData = new byte[16];
	  int stopFlag = 0;

	  // reset RecoverROM and generate the starting TData
	  RecoverROM(null,TData);

	  OWList.clear();
	  
	  for( int i = 0; i < 30; i++) {
		  	
		  if(stopFlag != 0 ) break;
		  
		  	reset();
			setDataMode();
			sendData(commandSearchRom);
			setCommandMode();
			setSearchAcceleratorOn();
			setDataMode();

		    // transmit the TDATA and receive the RDATA.
		    for(loop=0;loop<16;loop++)
		    {
		    	
		    	byte[] out = new byte[1];
		    	out[0] = TData[loop];
		    	
		    	comPort.writeBytes(out, 1);
		    	
		    	byte[] in = new byte[1];
		    	
				comPort.readBytes(in, 1);
				
				RData[loop] = in[0];
		    }

		    //decode recovered ROM and generate next Search value
		    stopFlag = RecoverROM(RData,TData);
		    
		    setCommandMode();
			setSearchAcceleratorOff();
			setDataMode();
			
			setCommandMode();
			reset();
	  }
	  
	  //System.out.println(OWList);
	  /*for( Byte[] b : OWList ) {
		  System.out.println(print(b));
	  }*/
	}
	
	public static String print(byte[] bytes) {
	    StringBuilder sb = new StringBuilder();
	    sb.append("[ ");
	    for (byte b : bytes) {
	        sb.append(String.format("0x%02X ", b));
	    }
	    sb.append("]");
	    System.out.println(sb.toString());
	    return sb.toString();
	}
	
	// used to keep track of which discrepancy bits have already been flipped.
	static byte[] TREE= new byte[64];
	public static int RecoverROM(byte[] ReceiveData, byte[] TransmitData)
	{
	  int loop;
	  int result;
	  byte[] TROM = new byte[64];   // the transmit value being generated
	  byte[] RROM = new byte[64];   // the ROM recovered from the received data
	  byte[] RDIS = new byte[64];   // the discrepancy bits in the received data

	  // If receivedata is NULL, this is the first run. Transmit data should be all
	  // zeros, and the discrepancy tree must also be reset.
	  if(ReceiveData == null)
	  {
	    for(loop = 0; loop < 64; loop++) TREE[loop] = 0;
	    for(loop = 0; loop < 16; loop++) TransmitData[loop] = 0;
	    return 1;
	  }
	  // de-interleave the received data into the new ROM code and the discrepancy bits
	  for(loop = 0; loop < 16; loop++)
	  {
	    if((ReceiveData[loop] & 0x02) == 0x00) RROM[loop*4] = 0; else RROM[loop*4 ] = 1;
	    if((ReceiveData[loop] & 0x08) == 0x00) RROM[loop*4+1] = 0; else RROM[loop*4+1] = 1;
	    if((ReceiveData[loop] & 0x20) == 0x00) RROM[loop*4+2] = 0; else RROM[loop*4+2] = 1;
	    if((ReceiveData[loop] & 0x80) == 0x00) RROM[loop*4+3] = 0; else RROM[loop*4+3] = 1;

	    if((ReceiveData[loop] & 0x01) == 0x00) RDIS[loop*4] = 0; else RDIS[loop*4 ] = 1;
	    if((ReceiveData[loop] & 0x04) == 0x00) RDIS[loop*4+1] = 0; else RDIS[loop*4+1] = 1;
	    if((ReceiveData[loop] & 0x10) == 0x00) RDIS[loop*4+2] = 0; else RDIS[loop*4+2] = 1;
	    if((ReceiveData[loop] & 0x40) == 0x00) RDIS[loop*4+3] = 0; else RDIS[loop*4+3] = 1;
	  }

	  // initialize the transmit ROM to the recovered ROM

	  for(loop = 0; loop < 64; loop++) TROM[loop] = RROM[loop];

	  // work through the new transmit ROM backwards setting every bit to 0 until the
	  // most significant discrepancy bit which has not yet been flipped is found.
	  // The transmit ROM bit at that location must be flipped.

	  for(loop = 63; loop >= 0; loop--)
	  {
	    // This is a new discrepancy bit. Set the indicator in the tree, flip the
	    // transmit bit, and then break from the loop.

	    if((TREE[loop] == 0) && (RDIS[loop] == 1) && (TROM[loop] == 0))
	    {
	      TREE[loop] = 1;
	      TROM[loop] = 1;
	      break;
	    }
	    if((TREE[loop] == 0) && (RDIS[loop] == 1) && (TROM[loop] == 1))
	    {
	      TREE[loop] = 1;
	      TROM[loop] = 0;
	      break;
	    }

	    // This bit has already been flipped, remove it from the tree and continue
	    // setting the transmit bits to zero.

	    if((TREE[loop] == 1) && (RDIS[loop] == 1)) TREE[loop] = 0;
	    TROM[loop] = 0;
	  }
	  result = loop;   // if loop made it to -1, there are no more discrepancy bits
	                   // and the search can end.

	  // Convert the individual transmit ROM bit into a 16 byte format
	  // every other bit is don't care.
	  for(loop = 0; loop < 16; loop++)
	  {
	    TransmitData[loop] = (byte) ((TROM[loop*4]<<1) +
	                         (TROM[loop*4+1]<<3) +
	                         (TROM[loop*4+2]<<5) +
	                         (TROM[loop*4+3]<<7));
	  }

	  // Convert the individual recovered ROM bits into an 8 byte format
	  byte[] addr = new byte[8];
	  for(loop = 0; loop < 8; loop++) {
		  
	    byte b = (byte) ((RROM[loop*8]) +
	                    (RROM[loop*8+1]<<1) +
	                    (RROM[loop*8+2]<<2) +
	                    (RROM[loop*8+3]<<3) +
	                    (RROM[loop*8+4]<<4) +
	                    (RROM[loop*8+5]<<5) +
	                    (RROM[loop*8+6]<<6) +
	                    (RROM[loop*8+7]<<7));
	    addr[loop] = b;
	  }
	  OWList.add(addr);
	  
	  if(result == -1) return 1;   // There are no DIS bits that haven't been flipped
	                               // Tell the main loop the seach is over
	  return 0;   // else continue
	}
	
	

	public static final byte[] toPrimitives(Byte[] oBytes)
	{
	    byte[] bytes = new byte[oBytes.length];

	    for(int i = 0; i < oBytes.length; i++) {
	        bytes[i] = oBytes[i];
	    }

	    return bytes;
	}
	

}
