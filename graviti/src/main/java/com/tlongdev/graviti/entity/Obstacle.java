package com.tlongdev.graviti.entity;

import com.badlogic.gdx.physics.box2d.FixtureDef;

import org.andengine.engine.camera.Camera;
import org.andengine.entity.Entity;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.PhysicsWorld;

public abstract class Obstacle {

    // ===========================================================
    // Constants
    // ===========================================================

    public static final FixtureDef WALL_FIXTURE_DEF = PhysicsFactory.createFixtureDef(0, 0.1f, 0.3f);

    // ===========================================================
    // Fields
    // ===========================================================

    protected float height;
    protected float width;
    protected float posX;
    protected float posY;

    protected Rectangle leftWall;
    protected Rectangle rightWall;

    // ===========================================================
    // Constructors
    // ===========================================================

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getPosX() {
        return posX;
    }

    public void setPosX(float posX) {
        this.posX = posX;
    }

    public float getPosY() {
        return posY;
    }

    public void setPosY(float posY) {
        this.posY = posY;
    }

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================

    // ===========================================================
    // Methods
    // ===========================================================

    public abstract void attachObstacle(Entity pScene, PhysicsWorld pPhysicsWorld);

    public abstract void detachObstacle(PhysicsWorld pPhysicsWorld);

    public boolean isVisible(Camera pCamera){
        return pCamera.getXMax() > posX - (width/2) &&
                pCamera.getXMin() < posX + (width/2) &&
                pCamera.getYMax() > posY - (height/2) &&
                pCamera.getYMin() < posY + (height/2);
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

}
