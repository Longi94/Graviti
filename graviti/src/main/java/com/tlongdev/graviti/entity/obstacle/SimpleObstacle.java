package com.tlongdev.graviti.entity.obstacle;

import android.util.Log;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.tlongdev.graviti.entity.Obstacle;

import org.andengine.entity.Entity;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.PhysicsWorld;
import org.andengine.opengl.vbo.VertexBufferObjectManager;

public class SimpleObstacle extends Obstacle {

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String LOG_TAG = SimpleObstacle.class.getSimpleName();
    // ===========================================================
    // Fields
    // ===========================================================

    private Rectangle leftRectangle;
    private Rectangle rightRectangle;
    private Body leftWallBody;
    private Body rightWallBody;
    private Body leftRectangleBody;
    private Body rightRectangleBody;

    // ===========================================================
    // Constructors
    // ===========================================================

    public SimpleObstacle(float x, float y, float cameraWidth, float cameraHeight,
                          VertexBufferObjectManager vbom){
        this(x, y, cameraWidth, cameraHeight, x, vbom);
    }

    public SimpleObstacle(float x, float y, float cameraWidth, float cameraHeight, float holePosX,
                          VertexBufferObjectManager vbom){

        width = cameraWidth;
        height = cameraHeight;
        posX = x;
        posY = y;

        leftWall = new Rectangle(-2, y, 2, cameraHeight, vbom);
        rightWall = new Rectangle(cameraWidth + 2, y, 2, cameraHeight, vbom);
        leftRectangle = new Rectangle((holePosX - cameraWidth / 10) / 2, y,
                                       holePosX - cameraWidth / 10, cameraHeight / 10, vbom);
        rightRectangle = new Rectangle(holePosX + cameraWidth / 10 + (cameraWidth - (holePosX + cameraWidth / 10)) / 2,
                                       y, cameraWidth - (holePosX + cameraWidth / 10),
                                       cameraHeight / 10, vbom);

        leftWall.setColor(0, 0, 0);
        rightWall.setColor(0, 0, 0);
        leftRectangle.setColor(0, 0, 0);
        rightRectangle.setColor(0, 0, 0);

    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================

    @Override
    public void attachObstacle(Entity pScene, PhysicsWorld pPhysicsWorld) {

        Log.d(LOG_TAG, "creating left wall body");
        leftWallBody = PhysicsFactory.createBoxBody(pPhysicsWorld, leftWall,
                        BodyDef.BodyType.StaticBody, WALL_FIXTURE_DEF);
        Log.d(LOG_TAG, "creating right wall body");
        rightWallBody = PhysicsFactory.createBoxBody(pPhysicsWorld, rightWall,
                BodyDef.BodyType.StaticBody, WALL_FIXTURE_DEF);
        Log.d(LOG_TAG, "creating left rectangle body");
        leftRectangleBody = PhysicsFactory.createBoxBody(pPhysicsWorld, leftRectangle,
                BodyDef.BodyType.StaticBody, WALL_FIXTURE_DEF);
        Log.d(LOG_TAG, "creating right rectangle body");
        rightRectangleBody = PhysicsFactory.createBoxBody(pPhysicsWorld, rightRectangle,
                BodyDef.BodyType.StaticBody, WALL_FIXTURE_DEF);

        Log.d(LOG_TAG, "attaching left wall body");
        pScene.attachChild(leftWall);
        Log.d(LOG_TAG, "attaching right wall body");
        pScene.attachChild(rightWall);
        Log.d(LOG_TAG, "attaching left rectangle body");
        pScene.attachChild(leftRectangle);
        Log.d(LOG_TAG, "attaching right rectangle body");
        pScene.attachChild(rightRectangle);
        Log.d(LOG_TAG, "finished attaching obstacle");
    }

    @Override
    public void detachObstacle(PhysicsWorld pPhysicsWorld) {
        rightWall.detachSelf();
        leftWall.detachSelf();
        rightRectangle.detachSelf();
        leftRectangle.detachSelf();

        rightWallBody.setActive(false);
        leftWallBody.setActive(false);
        rightRectangleBody.setActive(false);
        leftRectangleBody.setActive(false);

        pPhysicsWorld.destroyBody(rightWallBody);
        pPhysicsWorld.destroyBody(leftWallBody);
        pPhysicsWorld.destroyBody(rightRectangleBody);
        pPhysicsWorld.destroyBody(leftRectangleBody);

        rightWall.dispose();
        leftWall.dispose();
        rightRectangle.dispose();
        leftRectangle.dispose();
    }

    // ===========================================================
    // Methods
    // ===========================================================

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
