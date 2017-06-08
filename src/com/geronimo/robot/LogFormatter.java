package com.geronimo.robot;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter  extends Formatter{

	private final Date dat = new Date();
	
	@Override
	public String format(LogRecord record) {
        dat.setTime(record.getMillis());
                
        return dat + ":" + record.getLevel().toString() + ":" + record.getThreadID() + ":" + record.getMessage();
	}

}
