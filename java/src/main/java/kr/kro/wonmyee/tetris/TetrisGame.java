package kr.kro.wonmyee.tetris;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TetrisGame extends Application {
    private static final int TILE_SIZE = 30;
    private static final int WIDTH = 10;
    private static final int HEIGHT = 20;
    private static final int TOP_MARGIN = 50;       // 상단 UI 영역 (점수 및 미리보기)
    private static final int PREVIEW_TILE_SIZE = 20;  // 미리보기용 타일 크기

    // 7가지 테트리스 블록 (I, O, T, S, Z, L, J)
    private static final int[][][] SHAPES = {
            { {1, 1, 1, 1} },           // I
            { {1, 1}, {1, 1} },         // O
            { {0, 1, 0}, {1, 1, 1} },    // T
            { {1, 1, 0}, {0, 1, 1} },    // S
            { {0, 1, 1}, {1, 1, 0} },    // Z
            { {1, 1, 1}, {1, 0, 0} },    // L
            { {1, 1, 1}, {0, 0, 1} }     // J
    };

    // 각 테트로미노마다 다른 색상 (SHAPES 순서와 일치)
    private static final Color[] SHAPE_COLORS = {
            Color.CYAN,    // I
            Color.YELLOW,  // O
            Color.PURPLE,  // T
            Color.GREEN,   // S
            Color.RED,     // Z
            Color.ORANGE,  // L
            Color.BLUE     // J
    };

    // 격자: 각 행은 길이 WIDTH의 int[] 배열 (0이면 빈 칸, 1 이상이면 해당 테트로미노 색상 인덱스+1)
    private List<int[]> grid = new ArrayList<int[]>();

    // 현재 블록과 다음 블록
    private int[][] currentShape;
    private int[][] nextShape = null;
    // 현재 블록과 다음 블록의 색상을 결정하기 위한 인덱스 (0~6)
    private int currentShapeIndex;
    private int nextShapeIndex;

    private int shapeX = 4, shapeY = 0;
    private boolean gameOver = false;
    private int score = 0;
    private Random random = new Random();

    // MediaPlayer를 클래스 필드에 보관하여 GC로부터 보호
    private MediaPlayer currentMediaPlayer;

    // 현재 블록의 위치가 유효한지 체크 (경계 및 겹침 확인)
    private boolean isValidPosition() {
        return isValidPositionAt(shapeX, shapeY);
    }

    // 특정 좌표(testX, testY)에 현재 블록을 놓았을 때 유효한지 체크
    private boolean isValidPositionAt(int testX, int testY) {
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] != 0) {
                    int newX = testX + x;
                    int newY = testY + y;
                    if (newX < 0 || newX >= WIDTH || newY >= HEIGHT) return false;
                    if (newY >= 0 && grid.get(newY)[newX] != 0) return false;
                }
            }
        }
        return true;
    }

    @Override
    public void start(Stage primaryStage) {
        Pane root = new Pane();
        // 캔버스 크기: 게임 영역 + 상단 UI 영역
        Canvas canvas = new Canvas(WIDTH * TILE_SIZE, TOP_MARGIN + HEIGHT * TILE_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        root.getChildren().add(canvas);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (gameOver) return;
                if (event.getCode() == KeyCode.LEFT) {
                    move(-1);
                } else if (event.getCode() == KeyCode.RIGHT) {
                    move(1);
                } else if (event.getCode() == KeyCode.DOWN) {
                    drop();
                } else if (event.getCode() == KeyCode.UP) {
                    rotate();
                } else if (event.getCode() == KeyCode.SPACE) { // 스페이스바: 하드 드롭
                    hardDrop();
                }
            }
        });

        primaryStage.setScene(scene);
        primaryStage.setTitle("TetrAI Java");
        primaryStage.show();

        initializeGrid();
        spawnShape();

        // 배경음악 재생 시작
        playNextTrack(1);

        AnimationTimer timer = new AnimationTimer() {
            private long lastTick = 0;

            @Override
            public void handle(long now) {
                if (lastTick == 0) {
                    lastTick = now;
                    draw(gc);
                    return;
                }
                long elapsedTime = now - lastTick;
                // 1초(1,000,000,000 ns)마다 블록 자동 하강
                if (elapsedTime > 1_000_000_000) {
                    update();
                    lastTick = now;
                }
                draw(gc);
            }
        };
        timer.start();
    }

    // 배경음악을 순서대로 재생하는 메서드 (MediaPlayer를 필드에 저장)
    private void playNextTrack(final int trackIndex) {
        URL resource = getClass().getResource(String.format("/theme%d.mp3", trackIndex));
        if (resource == null) {
            // 더 이상 파일이 없으면 theme1.mp3부터 다시 재생
            playNextTrack(1);
            return;
        }
        Media media = new Media(resource.toExternalForm());
        currentMediaPlayer = new MediaPlayer(media);  // 필드에 저장
        currentMediaPlayer.setOnEndOfMedia(() -> {
            currentMediaPlayer.dispose();
            playNextTrack(trackIndex + 1);
        });
        currentMediaPlayer.play();
    }

    // 격자 초기화: 높이 HEIGHT의 각 행에 WIDTH 길이의 배열 생성
    private void initializeGrid() {
        for (int i = 0; i < HEIGHT; i++) {
            grid.add(new int[WIDTH]);
        }
    }

    // 현재 블록 생성 시 다음 블록 미리보기 업데이트
    private void spawnShape() {
        if (nextShape == null) {
            nextShapeIndex = random.nextInt(SHAPES.length);
            nextShape = SHAPES[nextShapeIndex];
        }
        currentShapeIndex = nextShapeIndex;
        currentShape = SHAPES[currentShapeIndex];
        nextShapeIndex = random.nextInt(SHAPES.length);
        nextShape = SHAPES[nextShapeIndex];
        shapeX = 4;
        shapeY = 0;
    }

    // 매 틱마다 블록 한 칸 하강
    private void update() {
        shapeY++;
        if (!isValidPosition()) {
            shapeY--;
            merge();
            clearLines();
            spawnShape();
            if (!isValidPosition()) {
                gameOver = true;
            }
        }
    }

    // 좌우 이동
    private void move(int direction) {
        shapeX += direction;
        if (!isValidPosition()) {
            shapeX -= direction;
        }
    }

    // 강제 하강 (DOWN 키)
    private void drop() {
        shapeY++;
        if (!isValidPosition()) {
            shapeY--;
            merge();
            clearLines();
            spawnShape();
            if (!isValidPosition()) {
                gameOver = true;
            }
        }
    }

    // 하드 드롭: 스페이스바 입력 시 호출되어 블록을 즉시 최하단으로 내린 후 병합 처리
    private void hardDrop() {
        shapeY = getGhostY();
        merge();
        clearLines();
        spawnShape();
        if (!isValidPosition()) {
            gameOver = true;
        }
    }

    // 블록 회전 (시계 방향)
    private void rotate() {
        int[][] rotatedShape = new int[currentShape[0].length][currentShape.length];
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                rotatedShape[x][currentShape.length - 1 - y] = currentShape[y][x];
            }
        }
        int oldX = shapeX;
        int[][] oldShape = currentShape;
        // 회전 후 모양의 폭 차이에 따른 보정
        shapeX -= (rotatedShape[0].length - currentShape[0].length) / 2;
        currentShape = rotatedShape;
        if (!isValidPosition()) {
            currentShape = oldShape;
            shapeX = oldX;
        }
    }

    // 현재 블록을 격자에 병합 (병합 시 테트로미노 인덱스+1을 저장)
    private void merge() {
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] != 0) {
                    if (shapeY + y >= 0) {
                        grid.get(shapeY + y)[shapeX + x] = currentShapeIndex + 1;
                    }
                }
            }
        }
    }

    // 가득 찬 줄을 제거하고 점수 부여
    // 1줄: 100점, 2줄: 300점, 3줄: 700점, 4줄 이상: 1000점
    private void clearLines() {
        int linesCleared = 0;
        for (int y = HEIGHT - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (grid.get(y)[x] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                grid.remove(y);
                grid.add(0, new int[WIDTH]);
                linesCleared++;
                y++; // 삭제된 줄 재확인
            }
        }
        if (linesCleared == 1) {
            score += 100;
        } else if (linesCleared == 2) {
            score += 300;
        } else if (linesCleared == 3) {
            score += 700;
        } else if (linesCleared >= 4) {
            score += 1000;
        }
    }

    // 현재 블록의 하드 드롭(ghost) 위치를 계산
    private int getGhostY() {
        int ghostY = shapeY;
        while (isValidPositionAt(shapeX, ghostY + 1)) {
            ghostY++;
        }
        return ghostY;
    }

    // 화면 그리기
    private void draw(GraphicsContext gc) {
        // 전체 캔버스 클리어 (상단 UI 영역 포함)
        gc.clearRect(0, 0, WIDTH * TILE_SIZE, TOP_MARGIN + HEIGHT * TILE_SIZE);

        // 상단 UI: 점수 표시
        gc.setFill(Color.BLACK);
        gc.setFont(new Font(20));
        gc.fillText("Score: " + score, 10, 30);

        // 상단 UI: 다음 블록 미리보기 (오른쪽)
        if (nextShape != null) {
            gc.setFont(new Font(15));
            gc.fillText("Next:", WIDTH * TILE_SIZE - 90, 20);
            int previewX = WIDTH * TILE_SIZE - 90;
            int previewY = 25;
            for (int y = 0; y < nextShape.length; y++) {
                for (int x = 0; x < nextShape[y].length; x++) {
                    if (nextShape[y][x] != 0) {
                        gc.setFill(SHAPE_COLORS[nextShapeIndex]);
                        gc.fillRect(previewX + x * PREVIEW_TILE_SIZE, previewY + y * PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE);
                        gc.setStroke(Color.BLACK);
                        gc.strokeRect(previewX + x * PREVIEW_TILE_SIZE, previewY + y * PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE, PREVIEW_TILE_SIZE);
                    }
                }
            }
        }

        // 격자에 병합된 블록 그리기 (상단 여백 적용)
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid.get(y)[x] != 0) {
                    int colorIndex = grid.get(y)[x] - 1;
                    gc.setFill(SHAPE_COLORS[colorIndex]);
                    gc.fillRect(x * TILE_SIZE, TOP_MARGIN + y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(x * TILE_SIZE, TOP_MARGIN + y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // ghost piece: 현재 블록이 수직 낙하했을 때 착지 위치를 반투명하게 표시
        int ghostY = getGhostY();
        Color baseColor = SHAPE_COLORS[currentShapeIndex];
        Color ghostColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 0.3);
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] != 0) {
                    int drawX = (shapeX + x) * TILE_SIZE;
                    int drawY = TOP_MARGIN + (ghostY + y) * TILE_SIZE;
                    gc.setFill(ghostColor);
                    gc.fillRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
                    gc.setStroke(ghostColor);
                    gc.strokeRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // 현재 이동 중인 블록 그리기 (일반 불투명하게)
        gc.setFill(SHAPE_COLORS[currentShapeIndex]);
        for (int y = 0; y < currentShape.length; y++) {
            for (int x = 0; x < currentShape[y].length; x++) {
                if (currentShape[y][x] != 0) {
                    int drawX = (shapeX + x) * TILE_SIZE;
                    int drawY = TOP_MARGIN + (shapeY + y) * TILE_SIZE;
                    gc.fillRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // 게임 오버 시 메시지 표시
        if (gameOver) {
            gc.setFill(Color.BLACK);
            gc.setFont(new Font(30));
            gc.fillText("Game Over", 70, TOP_MARGIN + HEIGHT * TILE_SIZE / 2);
        }
    }

    public static void main(String[] args) {
        Application.launch(TetrisGame.class, args);
    }
}
