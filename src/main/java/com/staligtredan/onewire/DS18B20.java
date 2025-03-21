package com.staligtredan.onewire;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DS18B20 implements OneWireCommands {

	public final static byte familyCode = 0x28;
	
	public final static byte resolution9Bits = 0x1F;
	public final static byte resolution10Bits = 0x3F;
	public final static byte resolution11Bits = 0x5F;
	public final static byte resolution12Bits = 0x7F;
	
	/* conversion time in ms */
	public final static int conversionTime9Bits = 94;
	public final static int conversionTime10Bits = 188;
	public final static int conversionTime11Bits = 375;
	public final static int conversionTime12Bits = 750;


	private static void log(Level level, String msg) {
		
		Logger.getLogger (DS2413.class.getName()).log(level, msg);
	}
	

	/**
	 * 
	 * @param adress
	 * @param resolution
	 * @return
	 */
	public static boolean setResolution( byte[] adress, byte resolution ) {

		// Bad adress length
		if( (adress != null) && (adress.length != 8) ) {

			log(Level.WARNING,"DS18B20 : bad adress length : expected 8 : got " + adress.length);

			return false;
		}
		
		byte resol = resolution12Bits;
		switch(resolution) {
			case 9: resol = resolution9Bits;
				break;
			case 10: resol = resolution10Bits;
				break;
			case 11: resol = resolution11Bits;
				break;
			case 12: resol = resolution12Bits;
				break;
		}

		DS2480B.reset();
		DS2480B.setDataMode();

		// Send to all
		if( (adress == null) || (adress.length == 0) ) {
			DS2480B.sendData(skipRom);
		}
		// Send to one
		else {
			DS2480B.sendData(matchRom);
			for ( int i = 0; i < 8; i++ ) {
				DS2480B.sendData(adress[i]);
			}
		}

		DS2480B.sendData(writeScratchpad);
		DS2480B.sendData((byte) 0);
		DS2480B.sendData((byte) 0);
		DS2480B.sendData(resol);

		DS2480B.setCommandMode();
		DS2480B.reset();

		return true;
	}



	/**
	 * 
	 * @param adress
	 * @param pullup
	 * @param pullupDuration
	 * @return
	 */
	public static boolean convert( byte[] adress, boolean pullup, byte[] pullupDuration ) {

		// Bad adress length
		if( (adress != null) && (adress.length != 8) ) {

			log(Level.WARNING,"DS18B20 : bad adress length : expected 8 : got " + adress.length);

			return false;
		}

		boolean resultOk = true;
		
		if( pullup )
			resultOk |= DS2480B.setPullupDuration(pullupDuration);
		
		resultOk |= DS2480B.reset();
		resultOk |= DS2480B.setDataMode();

		// Send to all
		if( (adress == null) || (adress.length == 0) ) {
			resultOk |= DS2480B.sendData(skipRom);
		}
		// Send to one
		else {
			resultOk |= DS2480B.sendData(matchRom);
			for ( int i = 0; i < 8; i++ ) {
				resultOk |= DS2480B.sendData(adress[i]);
			}
		}

		if( pullup ) {

			resultOk |= DS2480B.setCommandMode();
			resultOk |= DS2480B.armStrongPullup(true);
			resultOk |= DS2480B.terminatePulse();
			resultOk |= DS2480B.setDataMode();
		}

		resultOk |= DS2480B.sendData(convert);

		if( pullup ) {

			resultOk |= DS2480B.waitPulse();
			resultOk |= DS2480B.setCommandMode();
			resultOk |= DS2480B.armStrongPullup(false);
			resultOk |= DS2480B.terminatePulse();
		}

		resultOk |= DS2480B.setCommandMode();
		resultOk |= DS2480B.reset();

		if( !resultOk ) {
			log(Level.INFO,"DS18B20 : error converting");
			return false;
		}
		
		return true;
	}



	/**
	 * 
	 * @param adress
	 * @return
	 */
	public static double readTemp( byte[] adress ) {

		byte[] result = new byte[9];

		// Bad adress length
		if( (adress != null) && (adress.length != 8) ) {

			log(Level.WARNING,"DS18B20 : bad adress length : expected 8 : got " + adress.length);

			return 0.0;
		}

		boolean resultOk = true;
		
		resultOk |= DS2480B.reset();
		resultOk |= DS2480B.setDataMode();

		// Send to all
		if( (adress == null) || (adress.length == 0) ) {
			resultOk |= DS2480B.sendData(skipRom);
		}
		// Send to one
		else {
			resultOk |= DS2480B.sendData(matchRom);
			for ( int i = 0; i < 8; i++ ) {
				resultOk |= DS2480B.sendData(adress[i]);
			}
		}

		resultOk |= DS2480B.sendData(readScratchpad);
		resultOk |= DS2480B.waitData(result);
		resultOk |= DS2480B.setCommandMode();
		resultOk |= DS2480B.reset();

		if( !resultOk ) {
			log(Level.INFO,"DS18B20 : error reading scratchpad");
			return 0;
		}
		
		double temp = (((result[1] & 0xFF) << 8) | (result[0] & 0xFF)) * 0.0625;
		Double truncatedDouble = BigDecimal.valueOf(temp).setScale(1, RoundingMode.HALF_UP).doubleValue();

		// Cas ou reboot de l'alim côté 1-Wire (µCoupure EDF) et pas raspi
		// Du coup il a reset de la sonde mais pas du Raspi
		if( truncatedDouble == 85.0 ) {
			truncatedDouble = 0.0;
			log(Level.INFO, "DS18B20 : Sensor reset @85.0C : adrr[6]=" + DS2480B.hexFormat(adress[6]) + " addr[7]="
				+ DS2480B.hexFormat(adress[7]));
		}
		
		// Impossible techniquement, mais ça arrive lorceque il y a pb d'adressage 1-wire
		// Un élément est manquant dans la liste par exemple (le resultat est 4095.9)
		if( truncatedDouble > 125.0 ) {
			
			log(Level.INFO, "DS18B20 : Sensor error @" + truncatedDouble + "C : adrr[6]=" + DS2480B.hexFormat(adress[6])
				+ " addr[7]=" + DS2480B.hexFormat(adress[7]));
			truncatedDouble = 0.0;
		}
			
		return truncatedDouble;
	}
	
	/**
	 * Returns the time in ms needed to convert the temperature
	 * 
	 * @param resolution in bits
	 * @return
	 */
	public static int conversionTimeMs(int resolution) {
		
		switch(resolution) {
			case 9:
				return conversionTime9Bits;
			
			case 10:
				return conversionTime10Bits;
			
			case 11:
				return conversionTime11Bits;
			
			case 12:
				return conversionTime12Bits;
				
			default:
				return conversionTime12Bits;
		}
	}
}
