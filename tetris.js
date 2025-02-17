// 상수 정의
const TILE_SIZE = 30;
const WIDTH = 10;
const HEIGHT = 20;
const TOP_MARGIN = 50;       // 상단 UI 영역 (점수 및 미리보기)
const PREVIEW_TILE_SIZE = 20;  // 미리보기용 타일 크기
const DROP_INTERVAL = 1000;    // 자동 하강 간격 (ms)

// 7가지 테트리스 블록 (I, O, T, S, Z, L, J)
const SHAPES = [
  [[1, 1, 1, 1]],                 // I
  [[1, 1],
   [1, 1]],                       // O
  [[0, 1, 0],
   [1, 1, 1]],                    // T
  [[1, 1, 0],
   [0, 1, 1]],                    // S
  [[0, 1, 1],
   [1, 1, 0]],                    // Z
  [[1, 1, 1],
   [1, 0, 0]],                    // L
  [[1, 1, 1],
   [0, 0, 1]]                     // J
];

// 각 블록에 해당하는 색상 (인덱스가 SHAPES 배열과 일치)
const SHAPE_COLORS = [
  "cyan",    // I
  "yellow",  // O
  "purple",  // T
  "green",   // S
  "red",     // Z
  "orange",  // L
  "blue"     // J
];

// 캔버스 및 컨텍스트 가져오기
const canvas = document.getElementById("gameCanvas");
const ctx = canvas.getContext("2d");

// 배경음악 설정 (theme1.mp3를 반복 재생)
// ※ autoplay 제한 때문에 플레이어의 첫 클릭 또는 터치 후 재생됩니다.
const bgMusic = new Audio('theme1.mp3');
bgMusic.loop = true;
bgMusic.volume = 0.5;

// 게임 상태 변수
let gameStarted = false;  // 시작 화면일 때 false
let gameOver = false;
let score = 0;

// 격자 초기화 (높이: HEIGHT, 너비: WIDTH; 0이면 빈 칸)
let grid = Array.from({ length: HEIGHT }, () => new Array(WIDTH).fill(0));

// 현재 블록 및 다음 블록 관련 변수
let currentShape;
let nextShape = null;
let currentShapeIndex;
let nextShapeIndex;
let shapeX = 4, shapeY = 0;

// 드롭 타이머 제어
let lastTime = 0;

// 터치 이벤트용 좌표 변수 (모바일용)
let touchStartX = 0;
let touchStartY = 0;
const touchThreshold = 30; // 스와이프 임계값 (픽셀)

// 현재 블록이 (posX, posY) 위치에서 올바른지 확인 (경계 및 겹침 체크)
function isValidPosition(shape, posX, posY) {
  for (let y = 0; y < shape.length; y++) {
    for (let x = 0; x < shape[y].length; x++) {
      if (shape[y][x] !== 0) {
        const newX = posX + x;
        const newY = posY + y;
        if (newX < 0 || newX >= WIDTH || newY >= HEIGHT) return false;
        if (newY >= 0 && grid[newY][newX] !== 0) return false;
      }
    }
  }
  return true;
}

// 새로운 블록 생성 (현재 블록은 다음 블록으로, 다음 블록은 무작위 생성)
function spawnShape() {
  if (!nextShape) {
    nextShapeIndex = Math.floor(Math.random() * SHAPES.length);
    nextShape = SHAPES[nextShapeIndex];
  }
  currentShapeIndex = nextShapeIndex;
  currentShape = SHAPES[currentShapeIndex];
  nextShapeIndex = Math.floor(Math.random() * SHAPES.length);
  nextShape = SHAPES[nextShapeIndex];
  shapeX = 4;
  shapeY = 0;
}

// 1회 업데이트: 블록 한 칸 하강, 충돌 시 병합 및 줄 제거
function update() {
  shapeY++;
  if (!isValidPosition(currentShape, shapeX, shapeY)) {
    shapeY--;
    merge();
    clearLines();
    spawnShape();
    if (!isValidPosition(currentShape, shapeX, shapeY)) {
      gameOver = true;
    }
  }
}

// 좌우 이동
function move(direction) {
  shapeX += direction;
  if (!isValidPosition(currentShape, shapeX, shapeY)) {
    shapeX -= direction;
  }
}

// 강제 하강 (Down 키 또는 수직 스와이프)
function drop() {
  shapeY++;
  if (!isValidPosition(currentShape, shapeX, shapeY)) {
    shapeY--;
    merge();
    clearLines();
    spawnShape();
    if (!isValidPosition(currentShape, shapeX, shapeY)) {
      gameOver = true;
    }
  }
}

// 시계 방향 회전 (탭 또는 위쪽 스와이프)
function rotate() {
  const rotatedShape = [];
  for (let x = 0; x < currentShape[0].length; x++) {
    rotatedShape[x] = [];
    for (let y = currentShape.length - 1; y >= 0; y--) {
      rotatedShape[x].push(currentShape[y][x]);
    }
  }
  const oldX = shapeX;
  const oldShape = currentShape;
  // 회전 후 폭 변화에 따른 보정
  shapeX -= Math.floor((rotatedShape[0].length - currentShape[0].length) / 2);
  currentShape = rotatedShape;
  if (!isValidPosition(currentShape, shapeX, shapeY)) {
    currentShape = oldShape;
    shapeX = oldX;
  }
}

// 현재 블록을 격자에 병합 (병합 시 블록 인덱스+1 저장)
function merge() {
  for (let y = 0; y < currentShape.length; y++) {
    for (let x = 0; x < currentShape[y].length; x++) {
      if (currentShape[y][x] !== 0) {
        if (shapeY + y >= 0) {
          grid[shapeY + y][shapeX + x] = currentShapeIndex + 1;
        }
      }
    }
  }
}

// 가득 찬 줄 제거 및 점수 부여
// 1줄: 100점, 2줄: 300점, 3줄: 700점, 4줄 이상: 1000점
function clearLines() {
  let linesCleared = 0;
  for (let y = HEIGHT - 1; y >= 0; y--) {
    let full = true;
    for (let x = 0; x < WIDTH; x++) {
      if (grid[y][x] === 0) {
        full = false;
        break;
      }
    }
    if (full) {
      grid.splice(y, 1);
      grid.unshift(new Array(WIDTH).fill(0));
      linesCleared++;
      y++; // 삭제 후 같은 줄 재확인
    }
  }
  if (linesCleared === 1) score += 100;
  else if (linesCleared === 2) score += 300;
  else if (linesCleared === 3) score += 700;
  else if (linesCleared >= 4) score += 1000;
}

// 시작 화면 그리기 (검은 배경, 중앙 "Play" 텍스트)
function drawStartScreen() {
  ctx.fillStyle = "black";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "white";
  ctx.font = "40px Arial";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("Play", canvas.width / 2, canvas.height / 2);
}

// 게임 화면 그리기 (게임 중 상태)
function drawGame() {
  // 캔버스 전체 클리어
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  // 상단 UI: 점수 표시
  ctx.fillStyle = "black";
  ctx.font = "20px Arial";
  ctx.textAlign = "start";
  ctx.textBaseline = "top";
  ctx.fillText("Score: " + score, 10, 10);

  // 상단 UI: 다음 블록 미리보기 (오른쪽)
  if (nextShape) {
    ctx.font = "15px Arial";
    ctx.fillText("Next:", canvas.width - 90, 10);
    const previewX = canvas.width - 90;
    const previewY = 15;
    for (let y = 0; y < nextShape.length; y++) {
      for (let x = 0; x < nextShape[y].length; x++) {
        if (nextShape[y][x] !== 0) {
          ctx.fillStyle = SHAPE_COLORS[nextShapeIndex];
          ctx.fillRect(previewX + x * PREVIEW_TILE_SIZE, previewY + y * PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE);
          ctx.strokeStyle = "black";
          ctx.strokeRect(previewX + x * PREVIEW_TILE_SIZE, previewY + y * PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE);
        }
      }
    }
  }

  // 격자에 병합된 블록 그리기 (상단 여백 적용)
  for (let y = 0; y < HEIGHT; y++) {
    for (let x = 0; x < WIDTH; x++) {
      if (grid[y][x] !== 0) {
        const colorIndex = grid[y][x] - 1;
        ctx.fillStyle = SHAPE_COLORS[colorIndex];
        ctx.fillRect(x * TILE_SIZE, TOP_MARGIN + y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        ctx.strokeStyle = "black";
        ctx.strokeRect(x * TILE_SIZE, TOP_MARGIN + y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
      }
    }
  }

  // 현재 이동 중인 블록 그리기
  if (currentShape) {
    ctx.fillStyle = SHAPE_COLORS[currentShapeIndex];
    for (let y = 0; y < currentShape.length; y++) {
      for (let x = 0; x < currentShape[y].length; x++) {
        if (currentShape[y][x] !== 0) {
          const drawX = (shapeX + x) * TILE_SIZE;
          const drawY = TOP_MARGIN + (shapeY + y) * TILE_SIZE;
          ctx.fillRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
          ctx.strokeStyle = "black";
          ctx.strokeRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
        }
      }
    }
  }

  // 게임 오버 시 메시지 표시
  if (gameOver) {
    ctx.fillStyle = "black";
    ctx.font = "30px Arial";
    ctx.textAlign = "center";
    ctx.fillText("Game Over", canvas.width / 2, TOP_MARGIN + (HEIGHT * TILE_SIZE) / 2);
  }
}

// 전체 그리기 함수 (상태에 따라 시작 화면 또는 게임 화면을 표시)
function draw() {
  if (!gameStarted) {
    drawStartScreen();
  } else {
    drawGame();
  }
}

// 키보드 이벤트 리스너 (데스크탑)
document.addEventListener("keydown", (event) => {
  if (!gameStarted || gameOver) return;
  switch (event.code) {
    case "ArrowLeft":
      move(-1);
      break;
    case "ArrowRight":
      move(1);
      break;
    case "ArrowDown":
      drop();
      break;
    case "ArrowUp":
      rotate();
      break;
  }
});

// 캔버스 클릭 시 게임 시작 (아직 시작되지 않았다면)
canvas.addEventListener("click", () => {
  if (!gameStarted) {
    gameStarted = true;
    // 사용자의 클릭 후 배경음악 재생 (autoplay 제한 회피)
    bgMusic.play().catch(e => console.log("오디오 재생 오류:", e));
    spawnShape();
  }
});

// 터치 이벤트 (모바일)
// 터치 시작: 시작 화면에서 게임 시작, 아니면 터치 좌표 기록
canvas.addEventListener("touchstart", (e) => {
  e.preventDefault();
  if (!gameStarted) {
    gameStarted = true;
    bgMusic.play().catch(e => console.log("오디오 재생 오류:", e));
    spawnShape();
    return;
  }
  let touch = e.changedTouches[0];
  touchStartX = touch.pageX;
  touchStartY = touch.pageY;
});

// 터치 종료: 스와이프 방향에 따라 좌우 이동, 하강 또는 회전 처리
canvas.addEventListener("touchend", (e) => {
  e.preventDefault();
  if (!gameStarted || gameOver) return;
  let touch = e.changedTouches[0];
  let dx = touch.pageX - touchStartX;
  let dy = touch.pageY - touchStartY;
  
  // 탭인 경우 (움직임이 작으면)
  if (Math.abs(dx) < touchThreshold && Math.abs(dy) < touchThreshold) {
    rotate();
    return;
  }
  
  // 수평 스와이프가 수직보다 클 때
  if (Math.abs(dx) > Math.abs(dy)) {
    if (dx > 0) {
      move(1);
    } else {
      move(-1);
    }
  } else {
    // 수직 스와이프: 아래쪽이면 하강, 위쪽이면 회전
    if (dy > 0) {
      drop();
    } else {
      rotate();
    }
  }
});

// 게임 루프: requestAnimationFrame을 사용하여 주기적으로 update & draw
function gameLoop(timestamp) {
  if (gameStarted && !gameOver) {
    if (!lastTime) lastTime = timestamp;
    const delta = timestamp - lastTime;
    if (delta > DROP_INTERVAL) {
      update();
      lastTime = timestamp;
    }
  }
  draw();
  requestAnimationFrame(gameLoop);
}

// 게임 루프 시작 (초기에는 시작 화면만 보여짐)
requestAnimationFrame(gameLoop);

