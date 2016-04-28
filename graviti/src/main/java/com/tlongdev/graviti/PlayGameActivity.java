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

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
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


public class PlayGameActivity extends BaseGameActivity implements SensorEventListener,
        IOnSceneTouchListener, ContactListener, GameHelper.GameHelperListener {

    // ===========================================================
    // Constants
    // ===========================================================

    public static final int CLIENT_GAMES = GameHelper.CLIENT_GAMES;
    private static final int CAMERA_HEIGHT = 1280;
    private static final int CAMERA_WIDTH = 768;
    private static final int SHIP_SIZE = 75;
    private final int MAX_FIFO_SIZE = 4;
    private final HUD MAIN_HUD = new HUD();


    // ===========================================================
    // Fields
    // ===========================================================

    private ArrayList<Obstacle> mObstacleFIFO;

    private Body mCameraBottomBody;

    private CustomCamera mCamera;

    private boolean mBound = false;
    private boolean mCameraMoving;
    private boolean mDebugLog = false;
    private boolean mPaused = true;
    private boolean mTouchHold;

    private Ellipse mTouchHint;

    private Entity mMainLayer;
    private Entity mSecondaryLayer;

    private Font mMainFont;

    // The game helper object. This class is mainly a wrapper around this object.
    private GameHelper mGameHelper;

    private int mNextObstacleIndex;
    protected int mRequestedClients = CLIENT_GAMES;
    private int mScore = 0;

    private PhysicsWorld mPhysicsWorld;

    private Random mGenerator = new Random();

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundMusicService backgroundMusic = ((BackgroundMusicService.LocalBinder) service).getService();
            backgroundMusic.setVolume(0.3f);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private Ship mMainShip;

    private SensorManager mSensorManager;
    private Sensor mGravitySensor;

    private Text mScoreText;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mGameHelper == null) {
            getGameHelper();
        }
        if (mGameHelper != null) {
            mGameHelper.setup(this);
        }
    }

    @Override
    protected synchronized void onResume() {
        super.onResume();
        if (!mBound) {
            bindService(new Intent(this, BackgroundMusicService.class), mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGameHelper.onStart(this);
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
    public void onCreateResources(OnCreateResourcesCallback pOnCreateResourcesCallback)
            throws IOException {

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGravitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        mPhysicsWorld = new FixedStepPhysicsWorld(
                60,
                new Vector2(0f, -SensorManager.GRAVITY_EARTH * 2),
                false,
                8,
                3);
        mPhysicsWorld.setContactListener(this);

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

        mMainShip = new Ship(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 4, SHIP_SIZE,
                mParticleTextureRegion, true, getVertexBufferObjectManager());

        mObstacleFIFO = new ArrayList<>();

        mMainFont = FontFactory.createFromAsset(mEngine.getFontManager(), mainFontTextureAtlas,
                getAssets(), getString(R.string.main_font_name), 100, true, Color.RED_ARGB_PACKED_INT);
        mMainFont.load();

        pOnCreateResourcesCallback.onCreateResourcesFinished();
    }

    @Override
    public void onCreateScene(OnCreateSceneCallback pOnCreateSceneCallback) throws IOException {
        Scene scene = new Scene();
        mMainLayer = new Entity(){
            @Override
            protected void onManagedUpdate(float pSecondsElapsed) {
                if (mPaused)
                    super.onManagedUpdate(0);
                else
                    super.onManagedUpdate(pSecondsElapsed);
            }
        };
        mSecondaryLayer = new Entity();
        scene.setOnSceneTouchListener(this);
        mMainLayer.registerUpdateHandler(mPhysicsWorld);
        scene.setBackground(new Background(Color.WHITE));

        scene.attachChild(mMainLayer);
        scene.attachChild(mSecondaryLayer);

        pOnCreateSceneCallback.onCreateSceneFinished(scene);
    }

    @Override
    public void onPopulateScene(Scene pScene, OnPopulateSceneCallback pOnPopulateSceneCallback)
            throws IOException {

        attachInitialElements();

        mMainLayer.registerUpdateHandler(
                new IUpdateHandler() {
                    @Override
                    public void onUpdate(float pSecondsElapsed) {
                        mMainShip.updateParticleSystem();

                        if (mTouchHold)
                            mMainShip.applyForce(2000);

                        if (mMainShip.getShipMesh().getY() >
                                mObstacleFIFO.get(mNextObstacleIndex).getPosY()) {
                            mScoreText.setText("" + ++mScore);
                            mNextObstacleIndex++;
                        }

                        if (mObstacleFIFO.get(mObstacleFIFO.size() - 1).isVisible(mCamera)) {
                            addObstacle();
                            while (mObstacleFIFO.size() > MAX_FIFO_SIZE)
                                removeObstacle();
                        }

                        if (mMainShip.getShipBody().getLinearVelocity().y < 0) {
                            if (mCameraMoving) {
                                mCameraMoving = false;
                                mCamera.setChaseEntity(null);
                                mCameraBottomBody.setTransform(mCameraBottomBody.getPosition().x,
                                        mCamera.getYMin() / 32, 0);
                            }
                        } else if (mMainShip.getShipMesh().getY() >=
                                mCamera.getCenterY() - mCamera.getHeight() / 4) {
                            if (!mCameraMoving) {
                                mCamera.setChaseEntity(mMainShip.getShipMesh());
                                mCameraMoving = true;
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
        mSensorManager.registerListener(this, mGravitySensor, SensorManager.SENSOR_DELAY_GAME);
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
                mTouchHold = true;
                mMainShip.enableParticles();
            } else if (pSceneTouchEvent.isActionUp()) {
                mTouchHold = false;
                mMainShip.disableParticles();
            }
        }
        if (mPaused) {
            mPaused = false;
            mTouchHint.detachSelf();
            mTouchHint.dispose();
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

        mMainShip.setRotation(shipDegree);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void beginContact(Contact contact) {
        if (contact.isTouching() && mMainShip.isContacted(contact)){
            killPlayer();
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
        mObstacleFIFO.add(new SimpleObstacle(CAMERA_WIDTH / 2,
                mObstacleFIFO.get(mObstacleFIFO.size() - 1).getPosY() + CAMERA_HEIGHT / 2,
                CAMERA_WIDTH,
                CAMERA_HEIGHT / 2,
                mGenerator.nextInt(CAMERA_WIDTH / 5 * 4) + CAMERA_WIDTH / 10,
                getVertexBufferObjectManager()));
        mObstacleFIFO.get(mObstacleFIFO.size() - 1).attachObstacle(mMainLayer, mPhysicsWorld);
    }

    private void removeObstacle(){
        --mNextObstacleIndex;
        mObstacleFIFO.get(0).detachObstacle(mPhysicsWorld);
        mObstacleFIFO.remove(0);
    }

    private void attachInitialElements(){

        mScoreText = new Text(CAMERA_WIDTH / 2, CAMERA_HEIGHT - 100, mMainFont,
                "0", 5, getVertexBufferObjectManager());

        mMainShip.attachShip(mMainLayer, mPhysicsWorld);

        mObstacleFIFO.add(new StartScene(mCamera.getCenterX(), mCamera.getCenterY(),
                CAMERA_WIDTH, CAMERA_HEIGHT, getVertexBufferObjectManager()));
        mObstacleFIFO.add(new SimpleObstacle(mCamera.getCenterX(), CAMERA_HEIGHT * 1.25f,
                CAMERA_WIDTH, CAMERA_HEIGHT / 2, mGenerator.nextInt(CAMERA_WIDTH / 5 * 4) + CAMERA_WIDTH / 10
                , getVertexBufferObjectManager()));

        for (Obstacle o : mObstacleFIFO)
            o.attachObstacle(mMainLayer, mPhysicsWorld);
        mNextObstacleIndex = 1;

        mCameraBottomBody = PhysicsFactory.createBoxBody(mPhysicsWorld,
                new Rectangle(CAMERA_WIDTH / 2, -2, CAMERA_WIDTH, 2, getVertexBufferObjectManager()),
                BodyDef.BodyType.StaticBody, Obstacle.WALL_FIXTURE_DEF);

        MAIN_HUD.attachChild(mScoreText);
        mCamera.setChaseEntity(mMainShip.getShipMesh());
        mCamera.setHUD(MAIN_HUD);

        mTouchHint = new Ellipse(CAMERA_WIDTH / 2, CAMERA_HEIGHT / 4, 250, 250, getVertexBufferObjectManager());
        mTouchHint.setColor(new Color(0, 0, 0, 0));
        mTouchHint.setDrawMode(DrawMode.TRIANGLE_FAN);
        mTouchHint.registerEntityModifier(new LoopEntityModifier(
                new AlphaModifier(3, 0.2f, 0)
        ));
        mTouchHint.registerEntityModifier(new LoopEntityModifier(
                new ScaleModifier(3, 0, 1, EaseQuadOut.getInstance())
        ));
        mSecondaryLayer.attachChild(mTouchHint);
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
            Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_normal), mScore);

            if (mScore == 0)
                Games.Achievements.unlock(getApiClient(), getString(R.string.achievement_noob));

            if (mScore >= 100)
                Games.Achievements.unlock(getApiClient(), getString(R.string.achievement_to_the_stars));
        }

        SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE);

        if (prefs.getInt(getString(R.string.pref_highscore_key), 0) < mScore){
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(getString(R.string.pref_highscore_key), mScore);
            editor.apply();
        }

        finish();
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
