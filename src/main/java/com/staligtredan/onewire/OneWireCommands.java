package com.staligtredan.onewire;

public interface OneWireCommands {
	
	public final static byte skipRom = (byte) 0xCC;
	public final static byte readRom = (byte) 0x33;
	public final static byte writeScratchpad = (byte) 0x4E;
	public final static byte readScratchpad = (byte) 0xBE;
	public final static byte convert = (byte) 0x44;
	public final static byte matchRom = (byte) 0x55;

	public final static byte accessWrite = (byte) 0x5A;
	public final static byte accessRead = (byte) 0xF5;
	
	
}
