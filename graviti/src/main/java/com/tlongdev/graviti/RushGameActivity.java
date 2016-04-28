package com.tlongdev.graviti;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.example.games.basegameutils.GameHelper;
import com.tlongdev.graviti.entity.Obstacle;
import com.tlongdev.graviti.entity.Ship;
import com.tlongdev.graviti.entity.obstacle.SimpleObstacle;
import com.tlongdev.graviti.entity.obstacle.StartScene;
import com.tlongdev.graviti.entity.primitive.Ellipse;
import com.tlongdev.graviti.service.BackgroundMusicService;

import org.andengine.engine.Engine;
import org.andengine.engine.camera.Camera;
import org.andengine.engine.camera.hud.HUD;
import org.andengine.engine.handler.IUpdateHandler;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.resolutionpolicy.FillResolutionPolicy;
import org.andengine.entity.Entity;
import org.andengine.entity.modifier.AlphaModifier;
import org.andengine.entity.modifier.LoopEntityModifier;
import org.andengine.entity.modifier.ScaleModifier;
import org.andengine.entity.primitive.DrawMode;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.entity.scene.IOnSceneTouchListener;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.scene.background.Background;
import org.andengine.entity.text.Text;
import org.andengine.extension.physics.box2d.FixedStepPhysicsWorld;
import org.andengine.extension.physics.box2d.PhysicsConnector;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.PhysicsWorld;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.font.Font;
import org.andengine.opengl.font.FontFactory;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.ui.activity.BaseGameActivity;
import org.andengine.util.adt.color.Color;
import org.andengine.util.modifier.ease.EaseQuadOut;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


public class RushGameActivity extends BaseGameActivity implements SensorEventListener,
        IOnSceneTouchListener, GameHelper.GameHelperListener {

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String LOG_TAG = RushGameActivity.class.getSimpleName();

    public static final int CLIENT_GAMES = GameHelper.CLIENT_GAMES;
    public static final int CAMERA_HEIGHT = 1280;
    public static final int CAMERA_WIDTH = 768;
    public static final int SHIP_SIZE = 75;
    private final int MAX_FIFO_SIZE = 4;
	private final HUD MAIN_HUD = new HUD();
	
	// ===========================================================
    // Fields
    // ===========================================================

    // The game helper object. This class is mainly a wrapper around this object.
    private GameHelper mGameHelper;

    private boolean mDebugLog = false;

    protected int mRequestedClients = CLIENT_GAMES;
	
	private SensorManager mSensorManager;
    private Sensor gravitySensor;
    private boolean touchHold;

    private Camera mCamera;
    private Scene mScene;
    private boolean mPaused = true;

    private Entity mainLayer;
    private Entity cameraLayer;
    private Entity secondaryLayer;
    private Entity entityToChase;
	
	private Font mainFont;
    private Text scoreText;
    private int mScore = 0;

    private PhysicsWorld mPhysicsWorld;

    private ArrayList<Obstacle> obstacleFIFO;
    private Random generator = new Random();

    private Ship mainShip;
    private int nextObstacleIndex;

    private Body cameraTopBody;
	
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
    private Body chaseBody;
    private Ellipse touchHint;

    private Timer timer;

    // ===========================================================
    // Constructors
    // ===========================================================

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    public GameHelper getGameHelper() {
        if (mGameHelper == null) {
            mGameHelper = new GameHelper(this, mRequestedClients);
            mGameHelper.enableDebugLog(mDebugLog);
            if (!getIntent().getBooleanExtra("signed_in", false)){
                mGameHelper.setMaxAutoSignInAttempts(0);
            }
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
    protected void onCreate(Bundle pSavedInstanceState) {
        super.onCreate(pSavedInstanceState);
        if (mGameHelper == null) {
            getGameHelper();
        }
        if (mGameHelper != null) {
            mGameHelper.setup(this);
        }
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
        mGameHelper.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public EngineOptions onCreateEngineOptions() {
        mCamera = new Camera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);

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
        FontFactory.setAssetBasePath("font/");

        BitmapTextureAtlas bitmapTextureAtlas = new BitmapTextureAtlas(
                mEngine.getTextureManager(), 100, 100);
        BitmapTextureAtlas mainFontTextureAtlas = new BitmapTextureAtlas(
                mEngine.getTextureManager(), 512, 512, TextureOptions.BILINEAR_PREMULTIPLYALPHA);

        ITextureRegion mParticleTextureRegion = BitmapTextureAtlasTextureRegionFactory.createFromAsset(
                bitmapTextureAtlas, this, "particle.png", 0, 0
        );

        mEngine.getTextureManager().loadTexture(bitmapTextureAtlas);

        mainShip = new Ship(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 4, SHIP_SIZE,
                mParticleTextureRegion, true, getVertexBufferObjectManager());

        obstacleFIFO = new ArrayList<>();

        mainFont = FontFactory.createFromAsset(mEngine.getFontManager(), mainFontTextureAtlas,
                getAssets(), getString(R.string.main_font_name), 100, true, Color.RED_ARGB_PACKED_INT);
        mainFont.load();

        pOnCreateResourcesCallback.onCreateResourcesFinished();
    }

    @Override
    public void onCreateScene(OnCreateSceneCallback pOnCreateSceneCallback) throws IOException {
        mainLayer = new Entity(){
            @Override
            protected void onManagedUpdate(float pSecondsElapsed) {
                if (mPaused)
                    super.onManagedUpdate(0);
                else
                    super.onManagedUpdate(pSecondsElapsed);
            }
        };
        cameraLayer = new Entity(){
            @Override
            protected void onManagedUpdate(float pSecondsElapsed) {
                if (mPaused)
                    super.onManagedUpdate(0);
                else
                    super.onManagedUpdate(pSecondsElapsed);
            }
        };
        secondaryLayer = new Entity();
        entityToChase = new Entity();

		mScene = new Scene();
        mScene.setOnSceneTouchListener(this);
        mainLayer.registerUpdateHandler(mPhysicsWorld);
        mScene.setBackground(new Background(Color.WHITE));

        mScene.attachChild(cameraLayer);
        mScene.attachChild(mainLayer);
        mScene.attachChild(secondaryLayer);

        pOnCreateSceneCallback.onCreateSceneFinished(mScene);
    }

    @Override
    public void onPopulateScene(Scene pScene, OnPopulateSceneCallback pOnPopulateSceneCallback) throws IOException {
		
		attachInitialElements();

        mScene.registerUpdateHandler(
                new IUpdateHandler() {
                    @Override
                    public void onUpdate(float pSecondsElapsed) {

                        Log.d(LOG_TAG, "Updating particle system...");
                        mainShip.updateParticleSystem();

                        Log.d(LOG_TAG, "checking if ship is still visible...");
                        if (!mCamera.isEntityVisible(mainShip.getShipMesh())){
                            killPlayer();
                            finish();
                        }

                        if (touchHold) {
                            Log.d(LOG_TAG, "applying force to ship");
                            mainShip.applyForce(2000);
                        }

                        if (mainShip.getShipMesh().getY() >
                                obstacleFIFO.get(nextObstacleIndex).getPosY()) {
                            Log.d(LOG_TAG, "increasing mScore...");
                            scoreText.setText("" + ++mScore);
                            nextObstacleIndex++;
                        }

                        if (obstacleFIFO.get(obstacleFIFO.size() - 1).isVisible(mCamera)) {
                            Log.d(LOG_TAG, "adding obstacle...");
                            addObstacle();
                            while(obstacleFIFO.size() > MAX_FIFO_SIZE) {
                                Log.d(LOG_TAG, "removing obstacle....");
                                removeObstacle();
                            }
                        }
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
        if (mPaused) {
            mPaused = false;
            touchHint.detachSelf();
            touchHint.dispose();

            timer = new Timer();
            timer.scheduleAtFixedRate(new SpeedUpTask(), 0, 1000);
        }
        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
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

        Log.d(LOG_TAG, "Trying to setTransform");
        mainShip.setRotation(shipDegree);
        Log.d(LOG_TAG, "setTransform completed");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSignInFailed() {

    }

    @Override
    public void onSignInSucceeded() {
        Games.setViewForPopups(getApiClient(), getWindow().getDecorView().findViewById(android.R.id.content));
    }

    // ===========================================================
    // Methods
    // ===========================================================

    private void addObstacle(){
        Log.d(LOG_TAG, "creating new obstacle...");
        obstacleFIFO.add(new SimpleObstacle(CAMERA_WIDTH / 2,
                obstacleFIFO.get(obstacleFIFO.size() - 1).getPosY() + CAMERA_HEIGHT / 2,
                CAMERA_WIDTH,
                CAMERA_HEIGHT / 2,
                generator.nextInt(CAMERA_WIDTH / 5 * 4) + CAMERA_WIDTH / 10,
                getVertexBufferObjectManager()));
        Log.d(LOG_TAG, "attaching obstacle");
        obstacleFIFO.get(obstacleFIFO.size() - 1).attachObstacle(mScene, mPhysicsWorld);
        Log.d(LOG_TAG, "done adding new obstacle");
    }

    private void removeObstacle(){
        --nextObstacleIndex;
        obstacleFIFO.get(0).detachObstacle(mPhysicsWorld);
        obstacleFIFO.remove(0);
    }

    private void attachInitialElements(){

        scoreText = new Text(CAMERA_WIDTH / 2, CAMERA_HEIGHT - 100, mainFont,
                "0", 5, getVertexBufferObjectManager());

        obstacleFIFO.add(new StartScene(mCamera.getCenterX(), mCamera.getCenterY(),
                CAMERA_WIDTH, CAMERA_HEIGHT, getVertexBufferObjectManager()));
        obstacleFIFO.add(new SimpleObstacle(mCamera.getCenterX(), CAMERA_HEIGHT * 1.25f,
                CAMERA_WIDTH, CAMERA_HEIGHT / 2, generator.nextInt(CAMERA_WIDTH / 5 * 4) + CAMERA_WIDTH / 10
                , getVertexBufferObjectManager()));

        for (Obstacle o : obstacleFIFO)
            o.attachObstacle(mainLayer, mPhysicsWorld);
        nextObstacleIndex = 1;

        cameraTopBody = PhysicsFactory.createBoxBody(mPhysicsWorld,
                new Rectangle(CAMERA_WIDTH / 2, CAMERA_HEIGHT + 2, CAMERA_WIDTH, 2, getVertexBufferObjectManager()),
                BodyDef.BodyType.KinematicBody, Obstacle.WALL_FIXTURE_DEF);
        cameraTopBody.setLinearVelocity(0, 3);

        mainShip.attachShip(mainLayer, mPhysicsWorld);
        MAIN_HUD.attachChild(scoreText);
        mCamera.setHUD(MAIN_HUD);

        entityToChase.setPosition(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 2);
        cameraLayer.attachChild(entityToChase);
        chaseBody = PhysicsFactory.createBoxBody(mPhysicsWorld, entityToChase,
                BodyDef.BodyType.KinematicBody, PhysicsFactory.createFixtureDef(0, 0, 0));
        mPhysicsWorld.registerPhysicsConnector(new PhysicsConnector(entityToChase, chaseBody));
        chaseBody.setLinearVelocity(0, 3);
        chaseBody.getFixtureList().get(0).setSensor(true);
        mCamera.setChaseEntity(entityToChase);

        touchHint = new Ellipse(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 4, 250, 250, getVertexBufferObjectManager());
        touchHint.setColor(new Color(0, 0, 0, 0));
        touchHint.setDrawMode(DrawMode.TRIANGLE_FAN);
        touchHint.registerEntityModifier(new LoopEntityModifier(
                new AlphaModifier(3, 0.2f, 0)
        ));
        touchHint.registerEntityModifier(new LoopEntityModifier(
                new ScaleModifier(3, 0, 1, EaseQuadOut.getInstance())
        ));
        secondaryLayer.attachChild(touchHint);
    }

    protected void signOut() {
        mGameHelper.signOut();
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

    private void killPlayer() {
        if (isSignedIn()) {
            Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_rush), mScore);

            if (mScore == 0)
                Games.Achievements.unlock(getApiClient(), getString(R.string.achievement_noob));

            if (mScore >= 100)
                Games.Achievements.unlock(getApiClient(), getString(R.string.achievement_to_the_stars));
        }

        SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE);

        if (prefs.getInt(getString(R.string.pref_highscore_rush_key), 0) < mScore){
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(getString(R.string.pref_highscore_rush_key), mScore);
            editor.apply();
        }

        finish();
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    private class SpeedUpTask extends TimerTask {
        @Override
        public void run() {
            if (chaseBody.getLinearVelocity().x <= 100) {
                chaseBody.setLinearVelocity(0, chaseBody.getLinearVelocity().y + 0.1f);
                cameraTopBody.setLinearVelocity(0, cameraTopBody.getLinearVelocity().y + 0.1f);
            }
        }
    }
}
