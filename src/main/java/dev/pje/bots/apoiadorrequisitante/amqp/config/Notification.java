package dev.pje.bots.apoiadorrequisitante.amqp.config;

import java.io.Serializable;

public class Notification implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 746778764817015666L;

	public Notification() {
    }

    private String notificationType;
    private String msg;

	@Override
	public String toString() {
		return "Notification [notificationType=" + notificationType + ", msg=" + msg + "]";
	}
}