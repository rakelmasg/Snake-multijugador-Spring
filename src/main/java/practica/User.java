package practica;

import org.springframework.web.socket.WebSocketSession;

public class User {

	WebSocketSession session;
	String name;
	
	public User(WebSocketSession session, String name) {
		this.session = session;
		this.name = name;
	}

	public WebSocketSession getSession() {
		return session;
	}

	public void setSession(WebSocketSession session) {
		this.session = session;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	
	
}
