package practica;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SnakeGame {

	private final static long TICK_DELAY = 100;

	private ConcurrentHashMap<Integer, Snake> snakes = new ConcurrentHashMap<>();
	private AtomicInteger numSnakes = new AtomicInteger();
	private ScheduledExecutorService scheduler;
	private Fruit fruit;
	boolean started = false;
	private ConcurrentLinkedQueue<User> queue;

	public SnakeGame() {
		this.fruit = new Fruit(100, "red");
		this.queue = new ConcurrentLinkedQueue<User>();
	}

	public void addSnake(Snake snake) {

		snakes.put(snake.getId(), snake);
		if (numSnakes.getAndIncrement() == 3) {
			startTimer();
		}

	}

	public Collection<Snake> getSnakes() {
		return snakes.values();
	}

	public synchronized void removeSnake(Snake snake) {

		if (snakes.remove(Integer.valueOf(snake.getId()), snake)) {
			this.numSnakes.decrementAndGet();
		}
	}

	private void tick() {

		try {

			for (Snake snake : getSnakes()) {
				snake.update(getSnakes(), fruit);
			}

			StringBuilder sb = new StringBuilder();
			for (Snake snake : getSnakes()) {
				sb.append(getLocationsAndScoresJson(snake));
				sb.append(',');
			}
			sb.deleteCharAt(sb.length() - 1);
			String f = String.format("\"fruit\": {\"color\": \"%s\",\"x\": %d,\"y\": %d}", fruit.getColor(),
					fruit.getPosition().x, fruit.getPosition().y);
			String msg = String.format("{\"type\": \"update\", \"snakes\" : [%s], %s}", sb.toString(), f);

			broadcast(msg);

		} catch (Throwable ex) {
			//System.err.println("Exception processing tick()");
			ex.printStackTrace(System.err);
		}
	}

	private String getLocationsAndScoresJson(Snake snake) {

		synchronized (snake) {

			StringBuilder sb = new StringBuilder();
			sb.append(String.format("{\"x\": %d, \"y\": %d}", snake.getHead().x, snake.getHead().y));
			for (Location location : snake.getTail()) {
				sb.append(",");
				sb.append(String.format("{\"x\": %d, \"y\": %d}", location.x, location.y));
			}

			return String.format("{\"id\":%d,\"body\":[%s],\"score\":%s}", snake.getId(), sb.toString(),
					snake.getScore());
		}
	}

	public void broadcast(String message) throws Exception {
		for (Snake snake : getSnakes()) {
			try {

				// System.out.println("Sending message " + message + " to snake
				// " + snake.getId());
				synchronized (snake) {
					snake.sendMessage(message);
				}

			} catch (Throwable ex) {
				System.err.println("Exception sending message to snake " + snake.getId());
				ex.printStackTrace(System.err);
				removeSnake(snake);
			}
		}
	}

	public void startTimer() {
		scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(() -> tick(), TICK_DELAY, TICK_DELAY, TimeUnit.MILLISECONDS);
		this.started = true;
	}

	public void stopTimer() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
		this.started = false;
	}

	public int getNumSnakes() {
		return this.numSnakes.get();
	}

	public boolean isStarted() {
		return this.started;
	}

	public boolean putInQueue(User u) {
			if(!this.queue.contains(u)){
			this.queue.add(u);	
			return true;
		}
		return false;

	}

	public synchronized User getNextInQueue() {
		if (this.queue.isEmpty())
			return null;
		return this.queue.remove();
	}

	public void unQueue(User u) {
		for (User user : queue) {
			if (user.getName().equals(u.getName()) && user.getSession().equals(u.getSession()))
				this.queue.remove(user);
		}
	}
}
