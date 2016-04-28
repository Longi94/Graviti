package com.tlongdev.graviti.entity.obstacle;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.tlongdev.graviti.entity.Obstacle;

import org.andengine.entity.Entity;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.PhysicsWorld;
import org.andengine.opengl.vbo.VertexBufferObjectManager;

public class StartScene extends Obstacle {

    // ===========================================================
    // Constants
    // ===========================================================

    // ===========================================================
    // Fields
    // ===========================================================

    private Rectangle ground;
    private Body groundBody;
    private Body leftWallBody;
    private Body rightWallBody;

    // ===========================================================
    // Constructors
    // ===========================================================

    public StartScene(float x, float y, float cameraWidth, float cameraHeight, VertexBufferObjectManager vbom) {
        width = cameraWidth;
        height = cameraHeight;
        posX = x;
        posY = y;

        leftWall = new Rectangle(-2, cameraHeight / 2, 2, cameraHeight, vbom);
        rightWall = new Rectangle(cameraWidth + 2, cameraHeight / 2, 2, cameraHeight, vbom);
        ground = new Rectangle(cameraWidth / 2, -2, cameraWidth, 2, vbom);

        leftWall.setColor(0, 0, 0);
        rightWall.setColor(0, 0, 0);
        ground.setColor(0, 0, 0);
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================

    @Override
    public void attachObstacle(Entity pScene, PhysicsWorld pPhysicsWorld) {
        leftWallBody = PhysicsFactory.createBoxBody(pPhysicsWorld, leftWall,
                BodyDef.BodyType.StaticBody, WALL_FIXTURE_DEF);
        rightWallBody = PhysicsFactory.createBoxBody(pPhysicsWorld, rightWall,
                BodyDef.BodyType.StaticBody, WALL_FIXTURE_DEF);
        groundBody = PhysicsFactory.createBoxBody(pPhysicsWorld, ground,
                BodyDef.BodyType.StaticBody, WALL_FIXTURE_DEF);


        pScene.attachChild(leftWall);
        pScene.attachChild(rightWall);
        pScene.attachChild(ground);
    }

    @Override
    public void detachObstacle(PhysicsWorld pPhysicsWorld) {
        rightWall.detachSelf();
        leftWall.detachSelf();
        ground.detachSelf();

        rightWallBody.setActive(false);
        groundBody.setActive(false);
        leftWallBody.setActive(false);

        pPhysicsWorld.destroyBody(groundBody);
        pPhysicsWorld.destroyBody(leftWallBody);
        pPhysicsWorld.destroyBody(rightWallBody);

        rightWall.dispose();
        leftWall.dispose();
        ground.dispose();
    }

    // ===========================================================
    // Methods
    // ===========================================================

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
