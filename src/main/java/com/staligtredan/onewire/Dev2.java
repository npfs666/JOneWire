package com.staligtredan.onewire;

public class Dev2 {

	public static String print(byte[] bytes) {
	    StringBuilder sb = new StringBuilder();
	    sb.append("[ ");
	    for (byte b : bytes) {
	        sb.append(String.format("0x%02X ", b));
	    }
	    sb.append("]");
	    return sb.toString();
	}
	
	public static void main( String[] args ) throws InterruptedException {
		
		byte[] sonde1 = { (byte) 0x28, (byte) 0xAA, (byte) 0xAB, (byte) 0xA2, (byte) 0x53, (byte) 0x14, (byte) 0x01,
				(byte) 0x76 };
		
		byte[] sonde2 = { (byte) 0x28, (byte) 0xAA, (byte) 0xE7, (byte) 0x52, (byte) 0x1A, (byte) 0x13, (byte) 0x02,
				(byte) 0xFC };
		
		byte[] sonde3 = { (byte) 0x28, (byte) 0xAA, (byte) 0xE7, (byte) 0xA3, (byte) 0x53, (byte) 0x14, (byte) 0x01,
				(byte) 0x31 };
		
		//byte[] relai1 = {(byte)0x3A,(byte)0x7B,(byte)0xF7,(byte)0x41,(byte)0x00,(byte)0x00,(byte)0x00, (byte)0x67};
		byte[] relai2 = { (byte) 0x3A, (byte) 0x82, (byte) 0xF7, (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) 0x83 };
		
		DS2480B.openPort("/dev/ttyAMA0", 9600);
		
		//DS2480B.readRom();
		
		DS2413.setOutputs(relai2, true, false);
		
		DS18B20.convert(null, false, null);
		double d = DS18B20.readTemp(sonde1);
		
		Thread.sleep(800);
		
		long a = System.currentTimeMillis();
		
		System.out.println("Temperature : " + d +" C");
		
		d = DS18B20.readTemp(sonde2);
		System.out.println("Temperature : " + d +" C");
		
		d = DS18B20.readTemp(sonde3);
		System.out.println("Temperature : " + d +" C");
		System.out.println("Temps pour 3 sondes : "+ (System.currentTimeMillis()-a)+"ms");
		
		DS2480B.close();
	}

}
