package com.staligtredan.onewire;

public class Dev {

	public static void main( String[] args ) {

	    long a, b;
		
		System.out.println("Lancement JOneWire");
		
		DS2480B.openPort("/dev/ttyAMA0", 9600);
		
		a = System.currentTimeMillis();
		DS18B20.setResolution(null, DS18B20.resolution12Bits);
		b = System.currentTimeMillis();
		System.out.println("setResol : " + (b-a) + "ms");
		
		a = System.currentTimeMillis();
		DS18B20.convert(null, false, null);
		b = System.currentTimeMillis();
		System.out.println("convert : " + (b-a) + "ms");
		
		a = System.currentTimeMillis();
		double d = DS18B20.readTemp(null);
		b = System.currentTimeMillis();
		System.out.println("read : " + (b-a) + "ms");
		
		System.out.println("Temperature : " + d +" C");
		
		
		DS2480B.close();
	}

}
