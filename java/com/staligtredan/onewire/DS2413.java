package com.staligtredan.onewire;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DS2413 implements OneWireCommands {

	public static byte pioAMask = (byte) 0xFE;
	public static byte pioBMask = (byte) 0xFD;
	
	public static byte pioAMaskReadBack = (byte) 0x03;
	public static byte pioBMaskReadBack = (byte) 0x0C;

	
	private static void log(Level level, String msg) {
		
		Logger.getLogger (DS2413.class.getName()).log(level, msg);
	}
	
	public static boolean setOutputs(byte[] adress, boolean pioA, boolean pioB) {
		
		// Bad adress length
		if( (adress != null) && (adress.length != 8) ) {
			
			log(Level.WARNING, "DS2413 : bad adress length : expected 8 : got "+adress.length);
			return false;
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
			for(int i = 0; i < 8; i++) {
				resultOk |= DS2480B.sendData(adress[i]);
			}
		}		
		resultOk |= DS2480B.sendData(accessWrite);
		
		byte register = (byte) 0xFF;
		if( pioA ) {
			register &= pioAMask;
		}
		if( pioB ) {
			register &= pioBMask;
		}

		resultOk |= DS2480B.sendData(register);
		resultOk |= DS2480B.sendData((byte)~register);
		
		// 1.01 : on check le résultat pour plus de fiablité & dépannage
		byte[] result = new byte[2];
		resultOk |= DS2480B.waitData(result);
		
		/**
		 * 1.01 : Ajout de la vérification des états de sorties après écriture
		 * Retour = 0xAA + data [complément x 4 | B latch state | B pin state | A latch state | A pin state]
		 */
		// Erreur du retour DS2413 (réponse attendue 0xAA)
		if( result[0] != (byte)0xAA ) {
			log(Level.WARNING, "DS2414 : wrong confirmation byte(not 0xAA) state "+DS2480B.hexFormat(result[0]));
			return false;
		}
		
		//Erreur de complément sur pioA
		if( (result[1]&0x30) != ((~result[1]<<4)&0x30) ) {
			log(Level.WARNING, "DS2414 : wrong complement on pioA("+pioA+") state "+DS2480B.hexFormat(result[1]));
			return false;
		}
		
		//Erreur de complément sur pioB
		if( (result[1]&0xC0) != ((~result[1]<<4)&0xC0) ) {
			log(Level.WARNING, "DS2414 : wrong complement on pioB(" + pioB + ") state " + DS2480B.hexFormat(result[1]));
			return false;
		}
				
		// Mauvais ou non changement de pioA
		if( pioA ) {

			if( (result[1] & pioAMaskReadBack) != 0x00 ) {
				log(Level.WARNING, "DS2414 : wrong reading on pioA("+pioA+") state "+DS2480B.hexFormat(result[1]));
				return false;
			}
		} else {
			
			if( (result[1] & pioAMaskReadBack) != 0x02 ) {
				log(Level.WARNING, "DS2414 : wrong reading on pioA("+pioA+") state "+DS2480B.hexFormat(result[1]));
				return false;
			}
		}
		
		// Mauvais ou non changement de pioB
		if( pioB ) {
			
			if( (result[1] & pioBMaskReadBack) != 0x00 ) {
				log(Level.WARNING, "DS2414 : wrong reading on pioB("+pioB+") state "+DS2480B.hexFormat(result[1]));
				return false;
			}
		} else {
			
			if( (result[1] & pioBMaskReadBack) != 0x0C ) {
				log(Level.WARNING, "DS2414 : wrong reading on pioB("+pioB+") state "+DS2480B.hexFormat(result[1]));
				return false;
			}
		}

		resultOk |= DS2480B.setCommandMode();
		resultOk |= DS2480B.reset();
		
		return resultOk;
	}
}
