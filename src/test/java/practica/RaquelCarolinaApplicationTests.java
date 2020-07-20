package practica;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.websocket.DeploymentException;
import org.junit.BeforeClass;
import org.junit.Test;

import practica.RaquelCarolinaApplication;

public class RaquelCarolinaApplicationTests {

	// Método para arrancar el servidor automáticamente al ejecutar los tests.
	@BeforeClass
	public static void startServer() {
		RaquelCarolinaApplication.main(new String[] { "--server.port=8080" });
	}

	// Test para comprobar que la conexión es correcta.
	@Test
	public void testConnection() throws Exception {

		WebSocketClient wsc = new WebSocketClient();
		wsc.connect("ws://127.0.0.1:8080/snake");
		wsc.disconnect();
	}

	// Test que verifica que después de una conexión, se recibe un mensaje con
	// el nombre del usuario introducido.
	@Test
	public void testUser() throws Exception {

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		WebSocketClient wsc0 = new WebSocketClient();

		wsc0.onMessage((session, msg) -> {
			System.out.println("TestMessage: " + msg);
			firstMsg.compareAndSet(null, msg);
			latch.countDown();
		});

		wsc0.connect("ws://127.0.0.1:8080/snake");
		System.out.println("Connected player");

		latch.await();

		String msg0 = firstMsg.get();

		assertTrue("The fist message should contain 'user-id', but it is " + msg0, msg0.contains("user-id"));

		wsc0.disconnect();
	}

	// Test inicio automático de juego: Se deberá comprobar que cuando 4
	// jugadores se conecten a
	// la misma sala el juego comenzará de forma automática cuando llegue el
	// cuarto jugador.
	// para crear el test de que se han unido 4 personas, y el juego empieza
	// automaticamente al llegar el cuarto.
	@Test
	public void testInicioAutomatico() throws Exception {
		// cada jugador debe estar en un hilo
		Executor executor = Executors.newFixedThreadPool(4);
		CountDownLatch createLatch = new CountDownLatch(1);
		CountDownLatch finalLatch = new CountDownLatch(4);
		AtomicReference<String> testMSG = new AtomicReference<String>();
		AtomicInteger numUser = new AtomicInteger(0);
		Runnable users = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onMessage((session, msg) -> {
				System.out.println("TestMessage: " + msg + " from Session " + session.getId());
				if (msg.contains("user-id")) {
					if (numUser.getAndIncrement() == 0) {
						try {
							wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
									+ session.getId() + "\"}");

						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						try {
							createLatch.await();
							try {
								wsc.sendMessage(
										"{\"type\": \"join-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
												+ session.getId() + "\"}");

							} catch (IOException e) {
								e.printStackTrace();
							}
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}

					}
				}
				if (msg.contains("join")) {
					createLatch.countDown();
				}

				if (msg.contains("wait")) {
				}

				if (msg.contains("start")) {
					testMSG.compareAndSet(null, msg);
					finalLatch.countDown();

				}
			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		for (int i = 0; i < 4; i++) {
			executor.execute(users);
		}

		finalLatch.await();
		String msg0 = testMSG.get();

		assertTrue("The message should contain 'start', but it is " + msg0, msg0.contains("start"));

	}

	/*
	 * //Test de inicio manual de juego: Se deberá comprobar que un juego puede
	 * inciarse con 2 //jugadores si el creador lo solicita.
	 */
	@Test
	public void testInicioManual() throws Exception {

		// cada jugador debe estar en un hilo
		CountDownLatch createLatch = new CountDownLatch(1);
		CountDownLatch finalLatch = new CountDownLatch(1);
		AtomicInteger joined = new AtomicInteger(0);
		AtomicReference<String> testMSG0 = new AtomicReference<String>();
		AtomicReference<String> testMSG1 = new AtomicReference<String>();

		Runnable user0 = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onMessage((session, msg) -> {
				System.out.println("TestMessage: " + msg + " from Session " + session.getId());
				if (msg.contains("user-id")) {
					try {
						wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
								+ session.getId() + "\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (msg.contains("join")) {
					createLatch.countDown();
					if (joined.get() == 1) {
						try {
							wsc.sendMessage("{\"type\":\"start-game\"}");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

				}

				if (msg.contains("wait")) {
				}

				if (msg.contains("start")) {
					testMSG0.compareAndSet(null, msg);
					finalLatch.countDown();
					try {
						wsc.disconnect();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		Runnable user1 = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onMessage((session, msg) -> {
				System.out.println("TestMessage: " + msg + " from Session " + session.getId());
				if (msg.contains("user-id")) {
					try {
						createLatch.await();
						try {
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
									+ session.getId() + "\"}");

						} catch (IOException e) {
							e.printStackTrace();
						}
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					joined.incrementAndGet();
				}
				if (msg.contains("join")) {
				}

				if (msg.contains("wait")) {
				}

				if (msg.contains("start")) {
					testMSG1.compareAndSet(null, msg);
					finalLatch.countDown();
					try {
						wsc.disconnect();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		Thread t0 = new Thread(user0);
		Thread t1 = new Thread(user1);
		t0.start();
		t1.start();

		finalLatch.await();
		String msg0 = testMSG0.get();
		String msg1 = testMSG1.get();

		assertTrue("The message0 should contain 'start', but it is " + msg0, msg0.contains("start"));
		assertTrue("The message1 should contain 'start', but it is " + msg1, msg1.contains("start"));

		t0.interrupt();
		t1.interrupt();
	}

	// Test de fin de juego con un jugador: Se deberá comprobar que un juego
	// finaliza cuando un único jugador queda en la sala.
	@Test
	public void testFinJuego() throws Exception {
		// cada jugador debe estar en un hilo
		CountDownLatch createLatch = new CountDownLatch(1);
		CountDownLatch finalLatch = new CountDownLatch(1);
		AtomicInteger joined = new AtomicInteger(0);
		AtomicReference<String> testMSG = new AtomicReference<String>();

		Runnable user0 = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onMessage((session, msg) -> {
				System.out.println("TestMessage: " + msg + " from Session " + session.getId());
				if (msg.contains("user-id")) {
					try {
						wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
								+ session.getId() + "\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (msg.contains("join")) {
					createLatch.countDown();
					if (joined.get() == 1) {
						try {
							wsc.sendMessage("{\"type\":\"start-game\"}");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}

				if (msg.contains("wait")) {
				}

				if (msg.contains("start")) {
					try {
						wsc.disconnect();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}
		};

		Runnable user1 = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onMessage((session, msg) -> {
				System.out.println("TestMessage: " + msg + " from Session " + session.getId());
				if (msg.contains("user-id")) {
					try {
						createLatch.await();
						try {
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
									+ session.getId() + "\"}");

						} catch (IOException e) {
							e.printStackTrace();
						}
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					joined.incrementAndGet();
				}
				if (msg.contains("join")) {
				}

				if (msg.contains("wait")) {
				}

				if (msg.contains("start")) {
				}

				if (msg.contains("win")) {
					testMSG.compareAndSet(null, msg);
					finalLatch.countDown();

				}
			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		Thread t0 = new Thread(user0);
		Thread t1 = new Thread(user1);
		t0.start();
		t1.start();

		finalLatch.await();
		String msg = testMSG.get();

		assertTrue("The message should contain 'win', but it is " + msg, msg.contains("win"));

		t0.interrupt();
		t1.interrupt();
	}

	// Test de espera para entrar: Se deberá comprobar que si un quinto
	// jugador quiere entrar en una sala, no entrará hasta que otro finalce,
	// momento en el que entrará de forma automática.
	@Test
	public void testEsperaEntrada() throws Exception {
		Executor executor = Executors.newFixedThreadPool(5);
		CountDownLatch createLatch = new CountDownLatch(1);
		CountDownLatch finalLatch = new CountDownLatch(1);
		CountDownLatch usersLatch = new CountDownLatch(4);
		AtomicInteger otherInt = new AtomicInteger(0);

		AtomicReference<String> testMSG = new AtomicReference<String>();
		AtomicInteger numUser = new AtomicInteger(0);
		Runnable users = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onMessage((session, msg) -> {
				System.out.println("TestMessage: " + msg + " from Session " + session.getId());
				if (msg.contains("user-id")) {
					if (numUser.getAndIncrement() == 0) {
						try {
							wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
									+ session.getId() + "\"}");

						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						try {
							createLatch.await();
							try {
								wsc.sendMessage(
										"{\"type\": \"join-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
												+ session.getId() + "\"}");

							} catch (IOException e) {
								e.printStackTrace();
							}
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
				}
				if (msg.contains("join")) {
					createLatch.countDown();
				}

				if (msg.contains("wait")) {
				}

				if (msg.contains("start")) {
					usersLatch.countDown();
				}

				if (msg.contains("update")) {
					if (otherInt.get() == 1) {
						try {
							wsc.disconnect();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		Runnable otherUser = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onMessage((session, msg) -> {
				System.out.println("TestMessage: " + msg + " from Session " + session.getId());
				if (msg.contains("user-id")) {
					try {
						wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"sala1\", \"user-name\":\"user"
								+ session.getId() + "\"}");

					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (msg.contains("join")) {
					finalLatch.countDown();
					testMSG.compareAndSet(null, msg);
				}

				if (msg.contains("wait")) {
				}

				if (msg.contains("start")) {
				}

				if (msg.contains("queued")) {
					otherInt.incrementAndGet();
				}
			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		for (int i = 0; i < 4; i++)
			executor.execute(users);

		usersLatch.await();
		executor.execute(otherUser);

		finalLatch.await();
		String msg0 = testMSG.get();

		assertTrue("The message should contain 'join', but it is " + msg0, msg0.contains("join"));

	}

	// Test de carga: Se deberá simular que 10 jugadores se conectan a la
	// misma vez al servidor.
	@Test
	public void testCarga() throws Exception {
		// cada jugador debe estar en un hilo
		Executor executor = Executors.newFixedThreadPool(10);
		CountDownLatch finalLatch = new CountDownLatch(10);
		AtomicReference<String> p1 = new AtomicReference<String>();
		AtomicInteger i2 = new AtomicInteger(0);
		AtomicInteger i3 = new AtomicInteger(0);
		AtomicInteger i4 = new AtomicInteger(0);
		AtomicInteger i5 = new AtomicInteger(0);
		AtomicReference<String> p6 = new AtomicReference<String>();
		AtomicInteger i7 = new AtomicInteger(0);
		AtomicInteger i8 = new AtomicInteger(0);
		AtomicInteger i9 = new AtomicInteger(0);
		AtomicInteger i10 = new AtomicInteger(0);

		AtomicInteger idA = new AtomicInteger(0);
		AtomicInteger idB = new AtomicInteger(5);
		AtomicReference<String> testMSG = new AtomicReference<String>();
		AtomicInteger numUser = new AtomicInteger(0);
		AtomicInteger createdA = new AtomicInteger(0);
		AtomicInteger createdB = new AtomicInteger(0);
		AtomicInteger finishA = new AtomicInteger(0);
		AtomicInteger finishB = new AtomicInteger(0);
		Runnable userA = () -> {

			WebSocketClient wsc = new WebSocketClient();

			wsc.onOpen((session) -> {
				idA.incrementAndGet();
				if (idA.get() == 1) {
					p1.compareAndSet(null, session.getId());
				}
			});
			wsc.onMessage((session, msg) -> {
				if (msg.contains("user-id")) {
					try {
						wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"sala" + session.getId()
								+ "\", \"user-name\":\"user" + session.getId() + "\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
					System.out.println("TestMessage: " + msg + " from Session " + session.getId());

				}

				if (msg.contains("wait")) {
					System.out.println("TestMessage: " + msg + " from Session " + session.getId());

					try {
						if (createdA.get() == 0) {
							Thread.sleep(2000);
						} else {
							Thread.sleep(10000);
						}
						wsc.sendMessage("{\"type\": \"leave-game\", \"id\":" + numUser.getAndIncrement() + "}");
					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}

				}

				if (msg.contains("updateScores")) {
					if (session.getId().equals(p1.get()) && createdA.get() == 0) {
						try {
							wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"salaA\", \"user-name\":\"user"
									+ session.getId() + "\"}");
							createdA.incrementAndGet();
							numUser.set(0);
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else if (createdA.get() == 1 && i2.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaA\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					} else if (createdA.get() == 1 && i3.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaA\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					} else if (createdA.get() == 1 && i4.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaA\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					} else if (createdA.get() == 1 && i5.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaA\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					} else if (finishA.get() != 0) {
						testMSG.compareAndSet(null, msg);
						finalLatch.countDown();
					}
				}

				if (msg.contains("queued")) {
					System.out.println("TestMessage: " + msg + " from Session " + session.getId());
					try {
						finishA.incrementAndGet();
						Thread.sleep(5000);
						wsc.sendMessage("{\"type\": \"cancel\", \"user-name\": \"user" + session.getId() + "\"}");
					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}
				}

			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		Runnable userB = () -> {
			WebSocketClient wsc = new WebSocketClient();
			wsc.onOpen((session) -> {
				idB.incrementAndGet();
				if (idB.get() == 6) {
					p6.compareAndSet(null, session.getId());
				}
			});
			wsc.onMessage((session, msg) -> {
				if (msg.contains("user-id")) {
					try {
						wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"sala" + session.getId()
								+ "\", \"user-name\":\"user" + session.getId() + "\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
					System.out.println("TestMessage: " + msg + " from Session " + session.getId());

				}

				if (msg.contains("wait")) {
					System.out.println("TestMessage: " + msg + " from Session " + session.getId());
					try {
						if (createdB.get() == 0) {
							Thread.sleep(2000);
							wsc.sendMessage("{\"type\": \"leave-game\", \"id\":" + numUser.getAndIncrement() + "}");
						} else {
							Thread.sleep(2000);
							wsc.sendMessage("{\"type\": \"leave-game\", \"id\":" + numUser.getAndIncrement() + "}");
						}
					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}

				}

				if (msg.contains("updateScores")) {
					if (session.getId().equals(p6.get()) && createdB.get() == 0) {
						try {
							wsc.sendMessage("{\"type\": \"create-room\", \"room-name\":\"salaB\", \"user-name\":\"user"
									+ session.getId() + "\"}");
							createdB.incrementAndGet();
						} catch (IOException e) {
							e.printStackTrace();
						}

					}  else if (createdB.get() == 1 && i7.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaB\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					} else if (createdB.get() == 1 && i8.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaB\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					} else if (createdB.get() == 1 && i9.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaB\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					} else if (createdB.get() == 1 && i10.incrementAndGet() == 1) {
						try {
							Thread.sleep(1000);
							wsc.sendMessage("{\"type\": \"join-room\", \"room-name\":\"salaB\", \"user-name\":\"user"
									+ session.getId() + "\"}");
						} catch (InterruptedException | IOException e) {
							e.printStackTrace();
						}

					}else if (finishB.get() != 0) {
						testMSG.compareAndSet(null, msg);
						finalLatch.countDown();
					}
				}
				if (msg.contains("queued")) {
					System.out.println("TestMessage: " + msg + " from Session " + session.getId());
					try {
						finishB.incrementAndGet();
						Thread.sleep(5000);
						wsc.sendMessage("{\"type\": \"cancel\", \"user-name\": \"user" + session.getId() + "\"}");
					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}
				}

			});

			try {
				wsc.connect("ws://127.0.0.1:8080/snake");
			} catch (DeploymentException | IOException | URISyntaxException e) {
				e.printStackTrace();
			}

		};

		for (int i = 0; i < 5; i++) {
			executor.execute(userA);
			executor.execute(userB);
		}

		finalLatch.await();
		String msg0 = testMSG.get();

		assertTrue("The message should contain 'updateScores', but it is " + msg0, msg0.contains("updateScores"));

	}

}