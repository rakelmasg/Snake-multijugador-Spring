package practica;

import java.util.Collection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//La clase ScoreController se encarga de responder a las peticiones GET.
@RestController
@RequestMapping("/highscores")
public class ScoreController {
			
	//Devuelve las 10 puntuaciones más altas almacenadas en el servidor.	
	@GetMapping
	public Collection<Score> highscores() {	
		//Pide a la clase SnakeHandler las puntuaciones.
		return SnakeHandler.getScores();
	}
}


