package practica;

public class Fruit {

	private int points;
	private String color;
	private Location position;
	
	public Fruit(int points, String color) {
		this.points = points;
		this.color = color;
		setRandomLocation();
	}			

	public void setRandomLocation() {
		position = Utils.getRandomLocation();
	}

	public Location getPosition() {
		return this.position;		
	}
	
	public int getPoints() {
		return this.points;
	}
	
	public String getColor() {
		return this.color;
	}
}