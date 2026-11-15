package com.moyettes.legacyvoicechat.log;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server-side logging without a hard log4j dependency: log4j where the game ships it (Ornithe), java.util.logging
 * otherwise (Poseidon/Bukkit have no log4j). Messages use log4j's {} placeholders.
 */
public final class VoiceLog {

	/** Where messages go; a platform may set its own (e.g. the plugin's logger). */
	public interface Sink {
		void log(int level, String message, Throwable error);
	}

	public static final int INFO = 0, WARN = 1, ERROR = 2;

	private static volatile Sink sink;

	private final String name;

	private VoiceLog(String name) {
		this.name = name;
	}

	public static VoiceLog get(String name) {
		return new VoiceLog(name);
	}

	public static void setSink(Sink newSink) {
		sink = newSink;
	}

	public void info(String message, Object... args) {
		log(INFO, message, args);
	}

	public void warn(String message, Object... args) {
		log(WARN, message, args);
	}

	public void error(String message, Object... args) {
		log(ERROR, message, args);
	}

	private void log(int level, String message, Object[] args) {
		Throwable error = args.length > 0 && args[args.length - 1] instanceof Throwable ? (Throwable) args[args.length - 1] : null;
		Sink s = sink;
		if (s == null) {
			s = sink = defaultSink(name);
		}
		s.log(level, format(message, args), error);
	}

	static String format(String message, Object[] args) {
		if (args.length == 0 || message == null) return message;
		StringBuilder out = new StringBuilder();
		int arg = 0, from = 0;
		for (int i = message.indexOf("{}"); i >= 0 && arg < args.length; i = message.indexOf("{}", from)) {
			out.append(message, from, i).append(args[arg++]);
			from = i + 2;
		}
		return out.append(message.substring(from)).toString();
	}

	private static Sink defaultSink(String name) {
		try {
			Class.forName("org.apache.logging.log4j.LogManager");
			return Log4jSink.create(name);
		} catch (Throwable notThere) {
			Logger jul = Logger.getLogger(name);
			return (level, message, error) -> jul.log(level == ERROR ? Level.SEVERE : level == WARN ? Level.WARNING : Level.INFO,
				"[" + name + "] " + message, error);
		}
	}
}
