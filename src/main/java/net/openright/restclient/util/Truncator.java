package net.openright.restclient.util;

import java.util.Optional;

public class Truncator {

	private Optional<?> o;
	private int maxLength;

	public Truncator(Optional<?> o, int maxLength) {
		this.o = o;
		this.maxLength = maxLength;
	}

	@Override
	public String toString() {
		return o.map(o -> o.toString())
				.map(s -> s.length() > maxLength ? s.substring(0, maxLength) : s)
				.orElse("No content");
	}

}
