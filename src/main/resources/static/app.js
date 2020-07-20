var username = "";
var roomname = "";
var Console = {};
let game = null;
let tout;

Console.log = (function(message) {
	var console = document.getElementById('console');
	var p = document.createElement('p');
	p.style.wordWrap = 'break-word';
	p.innerHTML = message;
	console.appendChild(p);
	while (console.childNodes.length > 25) {
		console.removeChild(console.firstChild);
	}
	console.scrollTop = console.scrollHeight;
});


class Snake {
	constructor() {
		this.snakeBody = [];
		this.color = null;
		this.name = null;
	}

	draw(context) {
		for (var pos of this.snakeBody) {
			context.fillStyle = this.color;
			context.fillRect(pos.x, pos.y,
				game.gridSize, game.gridSize);
		}
	}
}


class Fruit {
	constructor() {
		this.x = null;
		this.y = null;
		this.color = null;
	}

	draw(context) {
			context.fillStyle = this.color;
			context.fillRect(this.x, this.y, game.gridSize, game.gridSize);
	}
}

function keyboard(e){
	var code = e.keyCode;
	if (code > 36 && code < 41) {
		switch (code) {
		case 37:
			if (game.direction != 'east')
				game.setDirection('west');
			break;
		case 38:
			if (game.direction != 'south')
				game.setDirection('north');
			break;
		case 39:
			if (game.direction != 'west')
				game.setDirection('east');
			break;
		case 40:
			if (game.direction != 'north')
				game.setDirection('south');
			break;
		}
	}
}

class Game {

	constructor(){
		
		this.numSnakes = null;
		this.fps = 30;
		this.nextFrame = null;
		this.interval = null;
		this.direction = 'none';
		this.gridSize = 10;
		this.nextGameTick = (new Date).getTime();
		this.fruit = null;
	}

	initialize() {	
		
		this.numSnakes = 0;
		this.snakes = [];
		let canvas = document.getElementById('playground');
		if (!canvas.getContext) {
			Console.log('Error: 2d canvas not supported by this browser.');
			return;
		}
		
		this.context = canvas.getContext('2d');
		window.addEventListener('keydown', keyboard, false);
		
	}

	setDirection(direction) {
		this.direction = direction;
		let msg = {"type":"direction","value":direction}
		socket.send(JSON.stringify(msg));
		Console.log('Sent: Direction ' + direction);
	}

	startGameLoop() {
		this.nextFrame = () => {
			requestAnimationFrame(() => this.run());
		}
		this.nextFrame();		
	}

	stopGameLoop() {
		this.nextFrame = null;
		if (this.interval != null) {
			clearInterval(this.interval);
		}
	}

	draw() {
		this.context.clearRect(0, 0, 640, 480);
		for (var id in this.snakes) {			
			this.snakes[id].draw(this.context);
		}
		if(this.fruit!=null)
			this.fruit.draw(this.context);
	}
	
	setFruit(color,x,y){
		this.fruit = new Fruit();
		this.fruit.color = color;
		this.fruit.x = x;
		this.fruit.y = y;
	}
	
	addSnake(id, color,name) {
		this.snakes[id] = new Snake();
		this.snakes[id].color = color;
		this.snakes[id].name = name;
	}
	
	addSnakes(snakes){
		let pnames = document.getElementById("player-names");
		pnames.innerHTML  = "";
		for (var j = 0; j < snakes.length; j++) {
			let id=snakes[j].id;
			let color=snakes[j].color;
			let name=snakes[j].name;
			let score=snakes[j].score;
			game.addSnake(id, color, name);
			pnames.innerHTML += "<span id='"+id+"' style='color:"+color+";'>"+name+": <span id='"+id+"-score'>"+score+"</span></span>";
			if(snakes.length==2){
				document.getElementById("start-btn").removeAttribute("disabled");
			}
		}
		this.numSnakes = snakes.length;
	}
	
	updateSnake(id, snakeBody, score) {
		if (this.snakes[id]) {
			this.snakes[id].snakeBody = snakeBody;
			let scoretag = id+"-score";
			document.getElementById(scoretag).innerHTML = score;
		}
	}

	removeSnake(id) {
		this.numSnakes -= 1;
		this.snakes[id] = null;
		let snaketag = 	document.getElementById(id);
		if(snaketag!=null && snaketag!=undefined){
			snaketag.parentNode.removeChild(snaketag);
		}
		// Force GC.
		delete this.snakes[id];
		if(this.numSnakes<2){
			document.getElementById("start-btn").setAttribute("disabled",true);
		}
	}

	run() {
	
		while ((new Date).getTime() > this.nextGameTick) {
			this.nextGameTick += this.skipTicks;
		}
		this.draw();
		if (this.nextFrame != null) {
			this.nextFrame();
		}
	}
}

function init(){
	escalar();
	highscoresAJAX();
	
	// Mientras el jugador no introduzca un nombre válido.
	 while(username==""||username==null){
		 username = prompt("Enter your name:");
	}
	
	// Muestra el nombre de usuario.
	document.getElementById("username").innerHTML ="Logged as: " + username;
	
	
	socket = new WebSocket("ws://127.0.0.1:8080/snake");

	socket.onopen = () => {
		// Socket open.. start the game loop.
		Console.log('Info: WebSocket connection opened.');
	}

	socket.onclose = () => {
		Console.log('Info: WebSocket closed.');
		if(game!=null)
			game.stopGameLoop();
	}

	socket.onmessage = (message) => {

		var packet = JSON.parse(message.data);
		
		switch (packet.type) {
		case 'update':
			if(game!=null){
				for (var i = 0; i < packet.snakes.length; i++) {
					game.updateSnake(packet.snakes[i].id, packet.snakes[i].body, packet.snakes[i].score);
				}
				game.setFruit(packet.fruit.color, packet.fruit.x, packet.fruit.y);
			}
			break;
		case 'start':
			game.startGameLoop();
			Console.log('Info: GAME STARTED.');
			Console.log('Info: Press an arrow key to begin.');
			document.getElementById("start-btn").style.display ="none";
			break;
		case 'join':
			clearTimeout(tout);
			if(game==null){ 
				createGame(function(){ 
					game.addSnakes(packet.snakes);
				});
			}else{
				game.addSnakes(packet.snakes);
			}
			break;
		case 'wait':
			Console.log('Info: '+packet.info);
			break;
		case 'leave':
			if(game!=null)
				game.removeSnake(packet.id);
			break;
		case 'dead':
			leaveGame();
			alert("Your snake is dead, bad luck!");
			break;
		case 'kill':
			Console.log('Info: Head shot!');
			break;
		case 'eat':					
			Console.log('Info: Tasty!');
			break;
		case 'win':					
			leaveGame();
			alert("CONGRATULATIONS! YOU WIN!");
			break;
		case 'updateScores':
			highscoresAJAX();
		break;	
		case 'queued':
			// nuevo div de espera con texto y boton cancel
			document.getElementById("queue-zone").style.display ="block";
			document.getElementById("menu").style.display="none";
			document.getElementById("highscores").style.display="none";
			tout = setTimeout(() => {
				cancelJoin()
			}, 5000);
			break;		
		case 'error':
			alert(packet.error);
			break;	
		case 'user-id':
				document.getElementById("userid").innerHTML = "User ID: #"+ packet.value;
				break;
		}
	}
	
}

function createRoom() {
	document.getElementById("start-btn").style.display ="inline";
	roomname = document.getElementById("room-request").value;
	if(roomname==""||roomname==null){
		alert("Enter room name:");
	}else{
		let msg = {"type":"create-room","user-name":username,"room-name":roomname}
		socket.send(JSON.stringify(msg));
	}
}

function joinRoom() {
	document.getElementById("start-btn").style.display ="none";
	roomname = document.getElementById("room-request").value;
	if(roomname==""||roomname==null){
		alert("Enter room name:");
	}else{
		let msg = {"type":"join-room","user-name":username,"room-name":roomname}
		socket.send(JSON.stringify(msg));
	}
}


function createGame(callback) {
	document.getElementById("menu").style.display="none";
	document.getElementById("highscores").style.display="none";
	document.getElementById("game-zone").style.display="block";
	document.getElementById("room-name").innerHTML="Room: "+roomname;
	document.getElementById("queue-zone").style.display ="none";
	game = new Game();
	game.initialize();
	callback();
}

function leaveGame() {
	window.removeEventListener('keydown', keyboard, false);
	game.stopGameLoop();
	game = null;
	socket.send("{\"type\":\"leave-game\"}");
	document.getElementById("game-zone").style.display="none";
	document.getElementById("room-name").innerHTML="";
	document.getElementById("start-btn").setAttribute("disabled",true);
	document.getElementById("start-btn").style.display ="none";
	document.getElementById('console').innerHTML="";
	document.getElementById("menu").style.display="block";
	document.getElementById("highscores").style.display="table";
}

function startGame() {
	document.getElementById("start-btn").style.display ="none";
	document.getElementById("start-btn").setAttribute("disabled",true);
	socket.send("{\"type\":\"start-game\"}");
}

function cancelJoin() {
	clearTimeout(tout);
	let msg = {"type":"cancel","user-name":username}
	socket.send(JSON.stringify(msg));
	document.getElementById("queue-zone").style.display ="none";
	document.getElementById("menu").style.display="block";
	document.getElementById("highscores").style.display="table";
	alert("Unable to join");
}

function highscoresAJAX(){
    xhr = new XMLHttpRequest();
    xhr.addEventListener('readystatechange',showHighscores);
    xhr.open('GET','http://localhost:8080/highscores',true);
    xhr.setRequestHeader("Content-Type", "application/json");
    xhr.send(null);				
} 

function showHighscores(evento){
    if(evento.target.readyState == 4){
        if(evento.target.status==200){
           	var hs = JSON.parse(evento.target.responseText);
            var t = document.getElementsByTagName('tbody')[0];
            t.innerHTML = "";
              for (var i = 0; i < hs.length; i++) {
            	  t.innerHTML += '<tr><td>'+(i+1)+'.</td><td>'+ hs[i].name+'</td><td>#'+ hs[i].id+'</td><td>'+hs[i].score+'</td></tr>';
              }
        }
    }
}












