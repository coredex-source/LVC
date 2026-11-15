package com.moyettes.legacyvoicechat.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Loaded only when log4j is on the classpath (VoiceLog checks first). */
final class Log4jSink {

	private Log4jSink() {
	}

	static VoiceLog.Sink create(String name) {
		Logger logger = LogManager.getLogger(name);
		return (level, message, error) -> {
			if (level == VoiceLog.ERROR) logger.error(message, error);
			else if (level == VoiceLog.WARN) logger.warn(message, error);
			else logger.info(message, error);
		};
	}
}
