package practica;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class Snake {

	private static final int DEFAULT_LENGTH = 5;

	private final int id;
	private Location head;
	private final Deque<Location> tail = new ArrayDeque<>();
	private int length = DEFAULT_LENGTH;
	private String name;
	private final String hexColor;
	private Direction direction;
	private AtomicInteger score = new AtomicInteger(0);
	private final WebSocketSession session;

	public Snake(int id, WebSocketSession session, String name) {
		this.name = name;
		this.id = id;
		this.session = session;
		this.hexColor = Utils.getRandomHexColor();
		resetState();
	}

	private void resetState() {
		this.direction = Direction.NONE;
		this.head = Utils.getRandomLocation();
		this.tail.clear();
		this.length = DEFAULT_LENGTH;
	}

	private synchronized void kill() throws Exception {
		resetState();
		sendMessage("{\"type\": \"dead\"}");
	}

	private synchronized void reward() throws Exception {
		score.addAndGet(300);
		this.length++;
		sendMessage("{\"type\": \"kill\"}");
	}

	private synchronized void reward(Fruit fruit) throws Exception {
		score.addAndGet(fruit.getPoints());
		this.length++;
		sendMessage("{\"type\": \"eat\"}");
	}

	protected  void sendMessage(String msg) throws Exception {
		this.session.sendMessage(new TextMessage(msg));
	}

	public synchronized void update(Collection<Snake> snakes, Fruit fruit) throws Exception {

		Location nextLocation = this.head.getAdjacentLocation(this.direction);

		if (nextLocation.x >= Location.PLAYFIELD_WIDTH) {
			nextLocation.x = 0;
		}
		if (nextLocation.y >= Location.PLAYFIELD_HEIGHT) {
			nextLocation.y = 0;
		}
		if (nextLocation.x < 0) {
			nextLocation.x = Location.PLAYFIELD_WIDTH;
		}
		if (nextLocation.y < 0) {
			nextLocation.y = Location.PLAYFIELD_HEIGHT;
		}

		if (this.direction != Direction.NONE) {
			this.tail.addFirst(this.head);
			if (this.tail.size() > this.length) {
				this.tail.removeLast();
			}
			this.head = nextLocation;
		}

		handleCollisions(snakes, fruit);
	}

	private void handleCollisions(Collection<Snake> snakes, Fruit fruit) throws Exception {
		// Fruit collision
		if (fruit.getPosition().equals(this.head)) {
			this.reward(fruit);
			fruit.setRandomLocation();
		}

		// Snake collisions
		for (Snake snake : snakes) {

			boolean headCollision = this.id != snake.id && snake.getHead().equals(this.head);

			boolean tailCollision = snake.getTail().contains(this.head);

			if (headCollision || tailCollision) {
				kill();
				if (this.id != snake.id) {
					snake.reward();
				}
			}
		}
	}

	public synchronized Location getHead() {
		return this.head;
	}

	public synchronized Collection<Location> getTail() {
		return this.tail;
	}

	public synchronized void setDirection(Direction direction) {
		this.direction = direction;
	}

	public int getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public synchronized int addAndGetScore(int points) {
		return this.score.addAndGet(points);
	}

	public synchronized int getScore() {
		return this.score.get();
	}

	public String getColor() {
		return this.hexColor;
	}

	public WebSocketSession getSession() {
		return session;
	}

}
