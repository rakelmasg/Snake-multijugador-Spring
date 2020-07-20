package practica;

import java.util.concurrent.atomic.AtomicInteger;

//La clase Score representa una puntuación compuesta por: nombre del jugador y partidas que ha ganado.
public class Score {

	private final String name;
	private final String id;
	private AtomicInteger score;

	// El valor por defecto de la puntuación es 0.
	public Score(String name, String id, int score) {
		this.name = name;
		this.id = id;
		this.score = new AtomicInteger(score);
	}

	// Se actualiza el score con la puntuación más alta: true si se actualiza, false si no
	public  boolean updateMaxScore(int newScore) {
		if (this.score.get() <= newScore) {
			this.score.set(newScore);
			return true;
		}
		return false;
	}

	// Devuelve la puntuación.
	public int getScore() {
		return this.score.get();
	}

	// Devuelve el nombre del jugador.
	public String getName() {
		return this.name;
	}
	public String getId(){
		return this.id;
	}


}
