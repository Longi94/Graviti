package com.tlongdev.graviti;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.WindowManager;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.OnInvitationReceivedListener;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateListener;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateListener;
import com.google.example.games.basegameutils.GameHelper;
import com.tlongdev.graviti.engine.CustomCamera;
import com.tlongdev.graviti.entity.Obstacle;
import com.tlongdev.graviti.entity.Ship;
import com.tlongdev.graviti.entity.obstacle.SimpleObstacle;
import com.tlongdev.graviti.entity.obstacle.StartScene;
import com.tlongdev.graviti.entity.primitive.Ellipse;
import com.tlongdev.graviti.service.BackgroundMusicService;

import org.andengine.engine.Engine;
import org.andengine.engine.camera.hud.HUD;
import org.andengine.engine.handler.IUpdateHandler;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.resolutionpolicy.FillResolutionPolicy;
import org.andengine.entity.primitive.DrawMode;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.entity.scene.IOnSceneTouchListener;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.scene.background.Background;
import org.andengine.extension.physics.box2d.FixedStepPhysicsWorld;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.PhysicsWorld;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.ui.activity.BaseGameActivity;
import org.andengine.util.adt.color.Color;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


public class MultiPlayerHardActivity extends BaseGameActivity implements GameHelper.GameHelperListener,
        RoomUpdateListener, RealTimeMessageReceivedListener, RoomStatusUpdateListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, OnInvitationReceivedListener,
        SensorEventListener, IOnSceneTouchListener, ContactListener {

    // ===========================================================
    // Constants
    // ===========================================================

    public static final String LOG_TAG = MultiPlayerEasyActivity.class.getSimpleName();

    // We expose these constants here because we don't want users of this class
    // to have to know about GameHelper at all.
    public static final int CLIENT_GAMES = GameHelper.CLIENT_GAMES;
    public static final int CLIENT_APPSTATE = GameHelper.CLIENT_APPSTATE;
    public static final int CLIENT_PLUS = GameHelper.CLIENT_PLUS;
    public static final int CLIENT_ALL = GameHelper.CLIENT_ALL;

    // request code for the "select players" UI
    // can be any number as long as it's unique
    final static int RC_SELECT_PLAYERS = 12345;

    // arbitrary request code for the waiting room UI.
// This can be any integer that's unique in your Activity.
    final static int RC_WAITING_ROOM = 12346;

    // at least 2 players required for our game
    final static int MIN_PLAYERS = 2;

    public static final int CAMERA_HEIGHT = 1280;
    public static final int CAMERA_WIDTH = 768;
    public static final int SHIP_SIZE = 75;
    private static final int MULTIPLAYER_HARD = 101;
    private static final char UPDATE_POSITION = 'U';
    private static final char WIN_GAME = 'W';
    private static final char LOOSE_GAME = 'L';
    private final int MAX_FIFO_SIZE = 4;
    private final HUD MAIN_HUD = new HUD();

    // ===========================================================
    // Fields
    // ===========================================================

    // are we already playing?
    boolean mPlaying = false;

    // Room ID where the currently active game is taking place; null if we're
    // not playing.
    String mRoomId = null;

    // The game helper object. This class is mainly a wrapper around this object.
    protected GameHelper mGameHelper;

    // Requested clients. By default, that's just the games client.
    protected int mRequestedClients = CLIENT_GAMES;
    protected boolean mDebugLog = true;

    private SensorManager mSensorManager;
    private Sensor gravitySensor;
    private boolean touchHold;

    private CustomCamera mCamera;
    private Scene mScene;
    private boolean cameraMoving;

    private boolean mPaused = true;

    private PhysicsWorld mPhysicsWorld;

    private ArrayList<Obstacle> obstacleFIFO;
    private Random generator = new Random();

    private Ship mainShip;
    private Ship enemyShip;

    private Ellipse playerIndicator;
    private Ellipse enemyIndicator;

    private Body cameraBottomBody;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            backgroundMusic = ((BackgroundMusicService.LocalBinder)service).getService();
            backgroundMusic.setVolume(0.3f);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };
    private BackgroundMusicService backgroundMusic;
    private boolean mBound = false;

    private ProgressDialog loadingDialog;

    private byte[] positionMessage;

    private Timer timer;

    private AlertDialog winnerDialog;
    private boolean dialogShown = false;

    // ===========================================================
    // Constructors
    // ===========================================================

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    protected void setRequestedClients(int requestedClients) {
        mRequestedClients = requestedClients;
    }

    public GameHelper getGameHelper() {
        if (mGameHelper == null) {
            mGameHelper = new GameHelper(this, mRequestedClients);
            mGameHelper.enableDebugLog(mDebugLog);
        }
        return mGameHelper;
    }

    protected GoogleApiClient getApiClient() {
        return mGameHelper.getApiClient();
    }

    protected boolean isSignedIn() {
        return mGameHelper.isSignedIn();
    }

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mGameHelper == null) {
            getGameHelper();
        }
        mGameHelper.setup(this);

    }

    @Override
    protected void onStart() {
        super.onStart();
        mGameHelper.onStart(this);
    }

    @Override
    protected synchronized void onResume() {
        super.onResume();
        if (!mBound) {
            bindService(new Intent(this, BackgroundMusicService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

        if (timer!= null)
            timer.cancel();
        // leave room
        if (mRoomId != null)
            Games.RealTimeMultiplayer.leave(getApiClient(), this, mRoomId);

        // remove the flag that keeps the screen on
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mGameHelper.onStop();
    }

    @Override
    protected void onActivityResult(int request, int response, Intent data) {
        mGameHelper.onActivityResult(request, response, data);

        if (request == RC_SELECT_PLAYERS) {
            if (response != Activity.RESULT_OK) {
                // user canceled
                return;
            }

            loadingDialog = ProgressDialog.show(MultiPlayerHardActivity.this, "",
                    "Please wait...", true);

            // get the invitee list
            Bundle extras = data.getExtras();
            final ArrayList<String> invitees =
                    data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

            // get auto-match criteria
            Bundle autoMatchCriteria = null;
            int minAutoMatchPlayers =
                    data.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
            int maxAutoMatchPlayers =
                    data.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

            if (minAutoMatchPlayers > 0) {
                autoMatchCriteria = RoomConfig.createAutoMatchCriteria(
                        minAutoMatchPlayers, maxAutoMatchPlayers, 0);
            } else {
                autoMatchCriteria = null;
            }

            // create the room and specify a variant if appropriate
            RoomConfig.Builder roomConfigBuilder = makeBasicRoomConfigBuilder();
            roomConfigBuilder.addPlayersToInvite(invitees);
            if (autoMatchCriteria != null) {
                roomConfigBuilder.setAutoMatchCriteria(autoMatchCriteria);
            }
            roomConfigBuilder.setVariant(MULTIPLAYER_HARD);
            RoomConfig roomConfig = roomConfigBuilder.build();
            Games.RealTimeMultiplayer.create(getApiClient(), roomConfig);

            // prevent screen from sleeping during handshake
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (request == RC_WAITING_ROOM) {
            if (response == Activity.RESULT_OK) {
                // TODO (start game)

                timer = new Timer();
                timer.scheduleAtFixedRate(new UnreliableMessageTask(), 0, 50);

                mPlaying = true;
                mPaused = false;
            }
            else if (response == Activity.RESULT_CANCELED) {
                if (timer!= null)
                    timer.cancel();
                // Waiting room was dismissed with the back button. The meaning of this
                // action is up to the game. You may choose to leave the room and cancel the
                // match, or do something else like minimize the waiting room and
                // continue to connect in the background.

                // in this example, we take the simple approach and just leave the room:
                if (mRoomId != null)
                    Games.RealTimeMultiplayer.leave(getApiClient(), this, mRoomId);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            else if (response == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
                if (timer!= null)
                    timer.cancel();
                // player wants to leave the room.
                if (mRoomId != null)
                    Games.RealTimeMultiplayer.leave(getApiClient(), this, mRoomId);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    @Override
    public void onSignInFailed() {
    }

    @Override
    public void onSignInSucceeded() {
        Games.setViewForPopups(getApiClient(), getWindow().getDecorView().findViewById(android.R.id.content));
        startMultiPlayerGame();
    }

    @Override
    public void onRoomCreated(int statusCode, Room room) {
        if (statusCode != GamesStatusCodes.STATUS_OK) {
            // let screen go to sleep
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // TODO show error message, return to main screen.
            if (!dialogShown)
                finish();
        }

        // get waiting room intent

        if (loadingDialog != null){
            loadingDialog.dismiss();
            loadingDialog = null;
        }

        mRoomId = room.getRoomId();

        Intent i = Games.RealTimeMultiplayer.getWaitingRoomIntent(getApiClient(), room, Integer.MAX_VALUE);
        startActivityForResult(i, RC_WAITING_ROOM);
    }

    @Override
    public void onJoinedRoom(int statusCode, Room room) {
        if (statusCode != GamesStatusCodes.STATUS_OK) {
            // let screen go to sleep
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // TODO show error message, return to main screen.
            if (!dialogShown)
                finish();
        }

        if (loadingDialog != null){
            loadingDialog.dismiss();
            loadingDialog = null;
        }

        mRoomId = room.getRoomId();

        // get waiting room intent
        Intent i = Games.RealTimeMultiplayer.getWaitingRoomIntent(getApiClient(), room, Integer.MAX_VALUE);
        startActivityForResult(i, RC_WAITING_ROOM);
    }

    @Override
    public void onLeftRoom(int statusCode, String s) {
        if (!dialogShown)
            finish();
    }

    @Override
    public void onRoomConnected(int statusCode, Room room) {
        if (statusCode != GamesStatusCodes.STATUS_OK) {
            // let screen go to sleep
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // TODO show error message, return to main screen.
            if (!dialogShown)
                finish();
        }
        mRoomId = room.getRoomId();
    }

    @Override
    public void onRealTimeMessageReceived(RealTimeMessage realTimeMessage) {
        byte[] buf = realTimeMessage.getMessageData();
        String sender = realTimeMessage.getSenderParticipantId();
        switch (buf[0]){
            case UPDATE_POSITION:
                byte[] xBytes = Arrays.copyOfRange(buf, 1, 5);
                byte[] yBytes = Arrays.copyOfRange(buf, 5, 9);
                byte[] rBytes = Arrays.copyOfRange(buf, 9, 13);

                float x = ByteBuffer.wrap(xBytes).getFloat();
                float y = ByteBuffer.wrap(yBytes).getFloat();
                float rotation = ByteBuffer.wrap(rBytes).getFloat();

                enemyShip.setPosition(x, y, rotation);
                break;
            case WIN_GAME:
                mPhysicsWorld.setContactListener(null);
                dialogShown = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MultiPlayerHardActivity.this);
                        builder.setMessage("You loooose!").setCancelable(false).
                                setPositiveButton(":(", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                });
                        winnerDialog = builder.create();
                        winnerDialog.show();
                    }
                });
                mPlaying = false;
                mainShip.getShipBody().setFixedRotation(false);
                break;
            case LOOSE_GAME:
                mPhysicsWorld.setContactListener(null);
                dialogShown = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MultiPlayerHardActivity.this);
                        builder.setMessage("You're Winner!").setCancelable(false).
                                setPositiveButton("Wooo!", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                });
                        winnerDialog = builder.create();
                        winnerDialog.show();
                    }
                });

                mPlaying = false;
                mainShip.getShipBody().setFixedRotation(false);

                Games.Achievements.increment(getApiClient(), getString(R.string.achievement_king), 1);
                Games.Achievements.increment(getApiClient(), getString(R.string.achievement_pro), 1);
                Games.Achievements.unlock(getApiClient(), getString(R.string.achievement_rookie));
                break;
            default:
                break;
        }
    }

    @Override
    public void onRoomConnecting(Room room) {

    }

    @Override
    public void onRoomAutoMatching(Room room) {

    }

    @Override
    public void onPeerInvitedToRoom(Room room, List<String> strings) {

    }

    @Override
    public void onPeerDeclined(Room room, List<String> strings) {
        // peer declined invitation -- see if game should be canceled
        if (!mPlaying && shouldCancelGame(room)) {
            if (timer!= null)
                timer.cancel();
            if (mRoomId != null)
                Games.RealTimeMultiplayer.leave(getApiClient(), this, mRoomId);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onPeerJoined(Room room, List<String> strings) {

    }

    @Override
    public void onPeerLeft(Room room, List<String> strings) {
        // peer left -- see if game should be canceled
        if (!mPlaying && shouldCancelGame(room)) {
            if (timer!= null)
                timer.cancel();
            if (mRoomId != null)
                Games.RealTimeMultiplayer.leave(getApiClient(), this, mRoomId);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (!dialogShown)
                finish();
        }
    }

    @Override
    public void onConnectedToRoom(Room room) {
        mRoomId = room.getRoomId();
    }

    @Override
    public void onDisconnectedFromRoom(Room room) {

        if (timer!= null)
            timer.cancel();
        // leave the room
        if (mRoomId != null)
            Games.RealTimeMultiplayer.leave(getApiClient(), this, mRoomId);
        mRoomId = null;

        // clear the flag that keeps the screen on
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // TODO show error message and return to main screen

        if (!dialogShown)
            finish();
    }

    @Override
    public void onPeersConnected(Room room, List<String> strings) {
        if (shouldStartGame(room)) {
            // TODO start game!
        }
    }

    @Override
    public void onPeersDisconnected(Room room, List<String> strings) {
        if (mPlaying) {
            // TODO do game-specific handling of this -- remove player's avatar
            // from the screen, etc. If not enough players are left for
            // the game to go on, end the game and leave the room.
        } else if (shouldCancelGame(room)) {
            // cancel the game
            if (timer!= null)
                timer.cancel();
            if (mRoomId != null)
                Games.RealTimeMultiplayer.leave(getApiClient(), this, mRoomId);
            mRoomId = null;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onP2PConnected(String s) {

    }

    @Override
    public void onP2PDisconnected(String s) {

    }

    @Override
    public EngineOptions onCreateEngineOptions() {
        mCamera = new CustomCamera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);

        EngineOptions engineOptions = new EngineOptions(
                true,
                ScreenOrientation.PORTRAIT_FIXED,
                new FillResolutionPolicy(),
                mCamera
        );

        engineOptions.getRenderOptions().setDithering(true);
        engineOptions.getRenderOptions().getConfigChooserOptions().setRequestedMultiSampling(true);

        return engineOptions;
    }

    @Override
    public Engine onCreateEngine(EngineOptions pEngineOptions) {
        return new Engine(pEngineOptions);
    }

    @Override
    public void onCreateResources(OnCreateResourcesCallback pOnCreateResourcesCallback) throws IOException {

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        mPhysicsWorld = new FixedStepPhysicsWorld(
                60,
                new Vector2(0f, -SensorManager.GRAVITY_EARTH * 2),
                false,
                8,
                3);

        BitmapTextureAtlasTextureRegionFactory.setAssetBasePath("particle/");
        BitmapTextureAtlas bitmapTextureAtlas = new BitmapTextureAtlas(
                mEngine.getTextureManager(), 100, 100);
        mPhysicsWorld.setContactListener(this);

        ITextureRegion mParticleTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(
                bitmapTextureAtlas, this, "particle.png", 0, 0
        );

        mEngine.getTextureManager().loadTexture(bitmapTextureAtlas);

        mainShip = new Ship(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 4, SHIP_SIZE,
                mParticleTextureRegion, true, getVertexBufferObjectManager());
        enemyShip = new Ship(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 4, SHIP_SIZE, Color.RED,
                mParticleTextureRegion, false, getVertexBufferObjectManager());

        obstacleFIFO = new ArrayList<>();

        pOnCreateResourcesCallback.onCreateResourcesFinished();
    }

    @Override
    public void onCreateScene(OnCreateSceneCallback pOnCreateSceneCallback) throws IOException {
        mScene = new Scene(){
            @Override
            protected void onManagedUpdate(float pSecondsElapsed) {
                if (mPaused)
                    super.onManagedUpdate(0);
                else
                    super.onManagedUpdate(pSecondsElapsed);
            }
        };
        mScene.setOnSceneTouchListener(this);
        mScene.registerUpdateHandler(mPhysicsWorld);
        mScene.setBackground(new Background(Color.WHITE));

        pOnCreateSceneCallback.onCreateSceneFinished(mScene);
    }

    @Override
    public void onPopulateScene(Scene pScene, OnPopulateSceneCallback pOnPopulateSceneCallback) throws IOException {
        attachInitialElements();

        mScene.registerUpdateHandler(
                new IUpdateHandler() {
                    @Override
                    public void onUpdate(float pSecondsElapsed) {
                        mainShip.updateParticleSystem();

                        if (isWinner() && !dialogShown){
                            mPhysicsWorld.setContactListener(null);
                            dialogShown = true;
                            byte[] message = {WIN_GAME};
                            Games.RealTimeMultiplayer.sendUnreliableMessageToOthers(getApiClient(), message, mRoomId);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MultiPlayerHardActivity.this);
                                    builder.setMessage("You're Winner!").setCancelable(false).
                                            setPositiveButton("Wooo!", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    finish();
                                                }
                                            });
                                    winnerDialog = builder.create();
                                    winnerDialog.show();
                                }
                            });

                            mPlaying = false;
                            mainShip.getShipBody().setFixedRotation(false);

                            Games.Achievements.increment(getApiClient(), getString(R.string.achievement_king), 1);
                            Games.Achievements.increment(getApiClient(), getString(R.string.achievement_pro), 1);
                            Games.Achievements.unlock(getApiClient(), getString(R.string.achievement_rookie));
                        }

                        if (touchHold && mPlaying)
                            mainShip.applyForce(2000);

                        if (obstacleFIFO.get(obstacleFIFO.size() - 1).isVisible(mCamera)) {
                            addObstacle();
                            while(obstacleFIFO.size() > MAX_FIFO_SIZE)
                                removeObstacle();
                        }

                        if (mainShip.getShipBody().getLinearVelocity().y < 0) {
                            if (cameraMoving) {
                                cameraMoving = false;
                                mCamera.setChaseEntity(null);
                                cameraBottomBody.setTransform(cameraBottomBody.getPosition().x,
                                        mCamera.getYMin() / 32, 0);
                            }
                        } else if (mainShip.getShipMesh().getY() >=
                                mCamera.getCenterY() - mCamera.getHeight() / 4) {
                            if (!cameraMoving) {
                                mCamera.setChaseEntity(mainShip.getShipMesh());
                                cameraMoving = true;
                            }
                        }

                        updateUnderBar();
                    }

                    @Override
                    public void reset() {

                    }
                }
        );

        pOnPopulateSceneCallback.onPopulateSceneFinished();
    }

    @Override
    public synchronized void onResumeGame() {
        super.onResumeGame();
        mSensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public synchronized void onPauseGame() {
        super.onPauseGame();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (!mPaused && mPlaying) {
            float x = sensorEvent.values[0];
            float y = Math.abs(sensorEvent.values[1]);

            float shipDegree = (float) Math.atan(y / x);

            if (x >= 0 && y >= 0 || x >= 0 && y < 0)
                shipDegree = (float) Math.PI - shipDegree;
            else if (x < 0 && y >= 0)
                shipDegree = -shipDegree;
            else
                shipDegree = 2 * (float) Math.PI - shipDegree;

            shipDegree -= Math.PI / 2;

            mainShip.setRotation(shipDegree);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public boolean onSceneTouchEvent(Scene pScene, TouchEvent pSceneTouchEvent) {
        if (mPhysicsWorld != null) {
            if (pSceneTouchEvent.isActionDown()) {
                touchHold = true;
                mainShip.enableParticles();
            } else if (pSceneTouchEvent.isActionUp()) {
                touchHold = false;
                mainShip.disableParticles();
            }
        }

        return true;
    }

    @Override
    public void beginContact(Contact contact) {
        if (contact.isTouching() && mainShip.isContacted(contact)){
            mPhysicsWorld.setContactListener(null);
            dialogShown = true;
            byte[] message = {LOOSE_GAME};
            Games.RealTimeMultiplayer.sendUnreliableMessageToOthers(getApiClient(), message, mRoomId);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MultiPlayerHardActivity.this);
                    builder.setMessage("You loooose!").setCancelable(false).
                            setPositiveButton(":(", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            });
                    winnerDialog = builder.create();
                    winnerDialog.show();
                }
            });
        }
    }

    @Override
    public void endContact(Contact contact) {

    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {

    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {

    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Games.Invitations.registerInvitationListener(getApiClient(), this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onInvitationReceived(Invitation invitation) {
        // TODO  show in-game popup to let user know of pending invitation

        // TODO store invitation for use when player accepts this invitation
        String mIncomingInvitationId = invitation.getInvitationId();
    }

    @Override
    public void onInvitationRemoved(String s) {

    }

// ===========================================================
    // Methods
    // ===========================================================

    // returns whether there are enough players to start the game
    boolean shouldStartGame(Room room) {
        int connectedPlayers = 0;
        for (Participant p : room.getParticipants()) {
            if (p.isConnectedToRoom()) ++connectedPlayers;
        }
        return connectedPlayers >= MIN_PLAYERS;
    }

    // Returns whether the room is in a state where the game should be canceled.
    boolean shouldCancelGame(Room room) {
        /*TODO: Your game-specific cancellation logic here. For example, you might decide to
         cancel the game if enough people have declined the invitation or left the room.
         You can check a participant's status with Participant.getStatus().
         (Also, your UI should have a Cancel button that cancels the game too)*/
        return false;
    }

    private void attachInitialElements() {

        enemyShip.attachShip(mScene, mPhysicsWorld);
        mainShip.attachShip(mScene, mPhysicsWorld);

        obstacleFIFO.add(new StartScene(mCamera.getCenterX(), mCamera.getCenterY(),
                CAMERA_WIDTH, CAMERA_HEIGHT, getVertexBufferObjectManager()));
        obstacleFIFO.add(new SimpleObstacle(mCamera.getCenterX(), CAMERA_HEIGHT * 1.25f,
                CAMERA_WIDTH, CAMERA_HEIGHT / 2, generator.nextInt(CAMERA_WIDTH / 5 * 4) + CAMERA_WIDTH / 10
                , getVertexBufferObjectManager()));

        for (Obstacle o : obstacleFIFO)
            o.attachObstacle(mScene, mPhysicsWorld);

        cameraBottomBody = PhysicsFactory.createBoxBody(mPhysicsWorld,
                new Rectangle(CAMERA_WIDTH / 2, -2, CAMERA_WIDTH, 2, getVertexBufferObjectManager()),
                BodyDef.BodyType.StaticBody, Obstacle.WALL_FIXTURE_DEF);

        Rectangle underBarBorder = new Rectangle(CAMERA_WIDTH / 2, 0, CAMERA_WIDTH, 100, getVertexBufferObjectManager());
        Rectangle underBar = new Rectangle(CAMERA_WIDTH / 2, 0, CAMERA_WIDTH, 90, getVertexBufferObjectManager());

        underBarBorder.setColor(Color.BLACK);
        underBar.setColor(Color.WHITE);

        MAIN_HUD.attachChild(underBarBorder);
        MAIN_HUD.attachChild(underBar);

        playerIndicator = new Ellipse(CAMERA_WIDTH / 2, 47.5f, 15, 15, getVertexBufferObjectManager());
        enemyIndicator = new Ellipse(CAMERA_WIDTH / 2, 47.5f, 15, 15, getVertexBufferObjectManager());

        playerIndicator.setDrawMode(DrawMode.TRIANGLE_FAN);
        enemyIndicator.setDrawMode(DrawMode.TRIANGLE_FAN);

        playerIndicator.setColor(Color.BLACK);
        enemyIndicator.setColor(Color.RED);

        MAIN_HUD.attachChild(enemyIndicator);
        MAIN_HUD.attachChild(playerIndicator);

        mCamera.setHUD(MAIN_HUD);
    }

    private void removeObstacle() {
        obstacleFIFO.get(0).detachObstacle(mPhysicsWorld);
        obstacleFIFO.remove(0);
    }

    private void addObstacle() {
        obstacleFIFO.add(new SimpleObstacle(CAMERA_WIDTH / 2,
                obstacleFIFO.get(obstacleFIFO.size() - 1).getPosY() + CAMERA_HEIGHT / 2,
                CAMERA_WIDTH,
                CAMERA_HEIGHT / 2,
                generator.nextInt(CAMERA_WIDTH / 5 * 4) + CAMERA_WIDTH / 10,
                getVertexBufferObjectManager()));
        obstacleFIFO.get(obstacleFIFO.size() - 1).attachObstacle(mScene, mPhysicsWorld);
    }

    private void updateUnderBar() {
        float difference = mainShip.getShipMesh().getY() - enemyShip.getShipMesh().getY();

        playerIndicator.setX(CAMERA_WIDTH / 2 + difference / 10);
        enemyIndicator.setX(CAMERA_WIDTH / 2 - difference / 10);
    }

    private void startMultiPlayerGame() {
        // launch the player selection screen
        // minimum: 1 other player; maximum: 3 other players
        Intent intent = Games.RealTimeMultiplayer.getSelectOpponentsIntent(getApiClient(), 1, 1);
        startActivityForResult(intent, RC_SELECT_PLAYERS);
    }

    // create a RoomConfigBuilder that's appropriate for your implementation
    private RoomConfig.Builder makeBasicRoomConfigBuilder() {
        return RoomConfig.builder(this)
                .setMessageReceivedListener(this)
                .setRoomStatusUpdateListener(this);
    }

    protected void showAlert(String message) {
        mGameHelper.makeSimpleDialog(message).show();
    }

    protected void showAlert(String title, String message) {
        mGameHelper.makeSimpleDialog(title, message).show();
    }

    protected void enableDebugLog(boolean enabled) {
        mDebugLog = true;
        if (mGameHelper != null) {
            mGameHelper.enableDebugLog(enabled);
        }
    }

    protected String getInvitationId() {
        return mGameHelper.getInvitationId();
    }

    protected void reconnectClient() {
        mGameHelper.reconnectClient();
    }

    protected boolean hasSignInError() {
        return mGameHelper.hasSignInError();
    }

    protected GameHelper.SignInFailureReason getSignInError() {
        return mGameHelper.getSignInError();
    }

    private boolean isWinner(){
        return playerIndicator.getX() - enemyIndicator.getX() > CAMERA_WIDTH / 10 * 9;
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    private class UnreliableMessageTask extends TimerTask {
        @Override
        public void run() {
            byte[] code = {UPDATE_POSITION};
            float x = mainShip.getShipMesh().getX();
            float y = mainShip.getShipMesh().getY();
            float rotation = mainShip.getRotation();

            positionMessage = concatByteArrays(code, concatByteArrays(float2ByteArray(x),
                    concatByteArrays(float2ByteArray(y), float2ByteArray(rotation))));
            Games.RealTimeMultiplayer.sendUnreliableMessageToOthers(getApiClient(), positionMessage, mRoomId);
        }

        public byte[] concatByteArrays(byte[] a, byte[] b) {
            int aLen = a.length;
            int bLen = b.length;
            byte[] c = new byte[aLen+bLen];
            System.arraycopy(a, 0, c, 0, aLen);
            System.arraycopy(b, 0, c, aLen, bLen);
            return c;
        }

        public byte[] float2ByteArray (float value)
        {
            return ByteBuffer.allocate(4).putFloat(value).array();
        }
    }
}