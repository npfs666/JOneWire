package com.staligtredan.onewire;

public class Dev3 {
	
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
		
		
		DS2480B.openPort("/dev/ttyAMA0", 9600);
		

		DS2480B.searchROMs();
		
		DS18B20.convert(DS2480B.toPrimitives(DS2480B.OWList.get(0)), false, null);
		Thread.sleep(1000);
		double tmp = DS18B20.readTemp(DS2480B.toPrimitives(DS2480B.OWList.get(0)));
		System.out.println(tmp);

		DS2480B.close();
	}

}
