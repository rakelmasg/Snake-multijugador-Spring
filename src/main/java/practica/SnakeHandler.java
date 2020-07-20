package practica;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SnakeHandler extends TextWebSocketHandler {

	private static final String SNAKE_ATT = "snake";
	private static final String ROOM_ATT = "room";

	private AtomicInteger snakeId = new AtomicInteger(0);
	private ConcurrentHashMap<String, SnakeGame> games = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String, Score> scores = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

	private ObjectMapper mapper = new ObjectMapper();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		System.out.println("Connection opened.");
		sessions.putIfAbsent(session.getId(), session);
		String msg = String.format("{\"type\": \"user-id\", \"value\": \"%s\"}", session.getId());
		synchronized (session) {
			session.sendMessage(new TextMessage(msg));
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		try {
			System.out.println("Message " + message.getPayload() + " received from user " + session.getId());
			JsonNode node = mapper.readTree(message.getPayload());
			String type = node.get("type").asText();

			if (type.equals("create-room")) {
				createRoom(session, node);

			} else if (type.equals("join-room")) {
				joinRoom(session, node);

			} else if (type.equals("start-game")) {
				startGame(session);

			} else if (type.equals("leave-game")) {
				leaveRoom(session, false);

			} else if (type.equals("direction")) {
				updateDirection(session, node);
			} else if (type.equals("cancel")) {
				cancelJoin(session, node);
			}

		} catch (Exception e) {
			System.err.println("Exception processing message " + message.getPayload());
			e.printStackTrace(System.err);
		}
	}

	private String snakeJSON(Snake s) {
		return String.format("{\"id\": %d, \"color\": \"%s\", \"name\": \"%s\", \"score\": \"%d\"}", s.getId(),
				s.getColor(), s.getName(), s.getScore());
	}

	private void createRoom(WebSocketSession session, JsonNode node) throws IOException {
		String msg;
		String roomName = node.get("room-name").asText();
		SnakeGame snakeGame = new SnakeGame();
		
		if (games.putIfAbsent(roomName, snakeGame)!=null) {
			
			int id = snakeId.getAndIncrement();
			Snake s = new Snake(id, session, node.get("user-name").asText());
			session.getAttributes().put(ROOM_ATT, roomName);
			snakeGame.addSnake(s);
			session.getAttributes().put(SNAKE_ATT, s);

			

			msg = String.format("{\"type\": \"join\",\"snakes\":[%s]}", snakeJSON(s));
			System.out.println("Sending message " + msg);
			synchronized (session) {
				session.sendMessage(new TextMessage(msg));
			}

			msg = String.format("{\"type\": \"wait\",\"info\":\"Waiting for more players...\",\"room\": \"%s\"}",
					roomName);
			System.out.println("Sending message " + msg);
			synchronized (session) {
				session.sendMessage(new TextMessage(msg));
			}
		} else {
			msg = String.format("{\"type\": \"error\",\"error\": \"%s\"}", "The room already exists");
			System.out.println("Sending message " + msg);
			synchronized (session) {
				session.sendMessage(new TextMessage(msg));
			}
		}
	}

	private void joinRoom(WebSocketSession session, JsonNode node) throws Exception {
		String roomName = node.get("room-name").asText();
		String msg;
		if (games.containsKey(roomName)) {
			SnakeGame snakeGame = games.get(roomName);

			if (snakeGame.getNumSnakes() == 4) {
				if (snakeGame.putInQueue(new User(session, node.get("user-name").asText()))) {
					session.getAttributes().put(ROOM_ATT, roomName);
					msg = "{\"type\": \"queued\"}";
					System.out.println("Sending message " + msg );
					synchronized (session) {
						session.sendMessage(new TextMessage(msg));
					}
				}
			} else {
				int id;
				Snake s;
				boolean started = snakeGame.isStarted();
				id = snakeId.getAndIncrement();
				s = new Snake(id, session, node.get("user-name").asText());
				snakeGame.addSnake(s);

				session.getAttributes().put(ROOM_ATT, roomName);
				session.getAttributes().put(SNAKE_ATT, s);

				StringBuilder sb = new StringBuilder();
				for (Snake snake : snakeGame.getSnakes()) {
					sb.append(snakeJSON(snake) + ",");
				}
				sb.deleteCharAt(sb.length() - 1);
				msg = String.format("{\"type\": \"join\",\"snakes\":[%s]}", sb.toString());
				snakeGame.broadcast(msg);

				if (started) {
					msg = "{\"type\": \"start\"}";
					System.out.println("Sending message " + msg+ " to username "+node.get("user-name").asText());
					synchronized (session) {
						session.sendMessage(new TextMessage(msg));
					}
				} else if (snakeGame.getNumSnakes() == 4) {
					msg = "{\"type\": \"start\"}";
					System.out.println("Broadcasting start in game " + roomName);
					snakeGame.broadcast(msg);
				} else {
					msg = String.format(
							"{\"type\": \"wait\",\"info\":\"Waiting for more players...\",\"room\": \"%s\"}", roomName);
					System.out.println("Sending message " + msg);
					synchronized (session) {
						session.sendMessage(new TextMessage(msg));
					}

				}
			}

		} else {
			msg = String.format("{\"type\": \"error\",\"error\": \"%s\"}", "The room does not exist");
			System.out.println("Sending message " + msg);
			synchronized (session) {
				session.sendMessage(new TextMessage(msg));
			}
		}

	}

	private void leaveRoom(WebSocketSession session, boolean disconnect) throws Exception {
		Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
		session.getAttributes().remove(SNAKE_ATT);
		String roomName = (String) session.getAttributes().get(ROOM_ATT);
		session.getAttributes().remove(ROOM_ATT);
		if (roomName != null) {
			SnakeGame sg = games.get(roomName);
			if (sg != null) {
				sg.removeSnake(s);
				if (sg.getNumSnakes() == 0) {
					games.remove(roomName);
				} else if (sg.getNumSnakes() == 1 && sg.isStarted()) {
					sg.stopTimer();
					sg.broadcast(String.format("{\"type\": \"win\", \"id\": %d}", s.getId()));
					games.remove(roomName);
				} else {
					sg.broadcast(String.format("{\"type\": \"leave\", \"id\": %d}", s.getId()));
					User newUser = sg.getNextInQueue();
					if (newUser != null) {
						WebSocketSession newSess = newUser.getSession();
						int id = snakeId.getAndIncrement();
						Snake newSnake = new Snake(id, newSess, newUser.getName());
						sg.addSnake(newSnake);
						newSess.getAttributes().put(SNAKE_ATT, newSnake);

						StringBuilder sb = new StringBuilder();
						for (Snake snake : sg.getSnakes()) {
							sb.append(snakeJSON(snake) + ",");
						}
						sb.deleteCharAt(sb.length() - 1);
						String msg = String.format("{\"type\": \"join\",\"snakes\":[%s]}", sb.toString());
						sg.broadcast(msg);

						msg = "{\"type\": \"start\"}";
						System.out.println("Sending message " + msg);
						synchronized (newSess) {
							newSess.sendMessage(new TextMessage(msg));
						}
					}
				}
			}
		}
		if (disconnect)
			sessions.remove(session.getId());
		if (s != null)
			updateScore(s);

	}

	private void startGame(WebSocketSession session) throws Exception {
		String roomName = (String) session.getAttributes().get(ROOM_ATT);
		SnakeGame sn = games.get(roomName);
		if (sn != null) {
			if (sn.getNumSnakes() > 1) {
				sn.startTimer();
				String msg = "{\"type\": \"start\"}";
				sn.broadcast(msg);
			}
		}
	}

	private void updateDirection(WebSocketSession session, JsonNode node) {
		Snake sn = (Snake) session.getAttributes().get(SNAKE_ATT);
		Direction d = Direction.valueOf(node.get("value").asText().toUpperCase());
		sn.setDirection(d);
	}

	private void cancelJoin(WebSocketSession session, JsonNode node) {
		String name = node.get("user-name").asText();
		String roomName = (String) session.getAttributes().get(ROOM_ATT);
		SnakeGame sn = games.get(roomName);
		if (sn != null) {
			sn.unQueue(new User(session, name));
		}
	}

	@Override
	public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		leaveRoom(session, true);
		System.out.println("Connection closed.");

	}

	// Actualiza una puntuación: almacena la puntuación o actualiza si existe.
	private void updateScore(Snake snake) throws IOException {
		Score sc;
		int newScore = snake.getScore();
		String name = snake.getName();
		WebSocketSession session = snake.getSession();

		// Si el jugador ya aparece en la tabla de highscores.
		if (scores.containsKey(session.getId())) {
			// Obtiene la puntuación.
			sc = scores.get(session.getId());
			// Se queda con la puntuación más alta.
			if (sc.updateMaxScore(newScore)) {
				scores.replace(session.getId(), sc);
			}
		} else {
			// Crea una nueva puntuación.
			sc = new Score(name, session.getId(), newScore);
			// Almacena en el servidor la nueva puntuación.
			scores.putIfAbsent(session.getId(), sc);

		}
		broadcast("{\"type\": \"updateScores\"}");
	}

	// Devuelve las puntuaciones de los 10 mejores jugadores.
	static public Collection<Score> getScores() {
		// Mete las puntuaciones en una lista para ordenarlas.
		ArrayList<Score> list = new ArrayList<Score>(scores.values());

		// Ordena de mayor a menor las puntuaciones.
		Collections.sort(list, (o1, o2) -> {
			if (o1 instanceof Score) {
				Score s1 = (Score) o1;
				Score s2 = (Score) o2;
				return new Integer(s2.getScore()).compareTo(new Integer(s1.getScore()));
			} else {
				return 0;
			}
		});

		// Si la lista es menor de 10.
		if (list.size() < 10) {
			// Devuelve los jugadores haya.
			return (Collection<Score>) list;
		} else {
			// Devuelve sólo los 10 primeros.
			return (Collection<Score>) list.subList(0, 10);
		}
	}

	private void broadcast(String msg) {
		for (WebSocketSession s : sessions.values()) {
			try {
				synchronized (s) {
					s.sendMessage(new TextMessage(msg));
				}
			} catch (IOException e) {
			//	e.printStackTrace();
			}

		}
	}

}
