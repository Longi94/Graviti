package com.tlongdev.graviti.entity;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.FixtureDef;

import org.andengine.entity.Entity;
import org.andengine.entity.particle.BatchedSpriteParticleSystem;
import org.andengine.entity.particle.emitter.PointParticleEmitter;
import org.andengine.entity.particle.initializer.ColorParticleInitializer;
import org.andengine.entity.particle.initializer.ExpireParticleInitializer;
import org.andengine.entity.particle.initializer.VelocityParticleInitializer;
import org.andengine.entity.particle.modifier.ColorParticleModifier;
import org.andengine.entity.primitive.DrawMode;
import org.andengine.entity.primitive.Mesh;
import org.andengine.entity.sprite.UncoloredSprite;
import org.andengine.extension.physics.box2d.PhysicsConnector;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.PhysicsWorld;
import org.andengine.extension.physics.box2d.util.constants.PhysicsConstants;
import org.andengine.extension.physics.box2d.util.triangulation.EarClippingTriangulator;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.opengl.vbo.VertexBufferObjectManager;
import org.andengine.util.adt.color.Color;
import org.andengine.util.adt.list.ListUtils;

import java.util.ArrayList;
import java.util.List;

public class Ship {

    // ===========================================================
    // Constants
    // ===========================================================

    private static final FixtureDef SHIP_BODY_FICTURE_DEF = PhysicsFactory.createFixtureDef(20f, 0f, 0.5f);

    // ===========================================================
    // Fields
    // ===========================================================

    private float rotation;
    private boolean isMain;
    
    private Mesh shipMesh;
    private Body shipBody;
    private List<Vector2> shipVerticesTriangulated;

    private PointParticleEmitter mParticleEmitter;
    private BatchedSpriteParticleSystem mParticleSystem;
    private VelocityParticleInitializer<UncoloredSprite> mVelocityInitializer;
    private ExpireParticleInitializer<UncoloredSprite> mExpireInitializer;

    // ===========================================================
    // Constructors
    // ===========================================================

    public Ship(int x, int y, float shipSize, Color pColor, ITextureRegion textureRegion, boolean main,
                VertexBufferObjectManager vbom){

        isMain = main;

        List<Vector2> shipVertices = new ArrayList<Vector2>();
        shipVertices.addAll(
                ListUtils.toList(
                        new Vector2(0, -shipSize / 4),
                        new Vector2(-shipSize / 2, -shipSize / 2),
                        new Vector2(0, shipSize / 2),
                        new Vector2(shipSize / 2, -shipSize / 2))
        );

        shipVerticesTriangulated = new EarClippingTriangulator().computeTriangles(shipVertices);

        float[] shipMeshTriangles = new float[shipVerticesTriangulated.size() * 3];
        for (int i = 0; i < shipVerticesTriangulated.size(); i++){
            shipMeshTriangles[i*3] = shipVerticesTriangulated.get(i).x;
            shipMeshTriangles[i*3 + 1] = shipVerticesTriangulated.get(i).y;
            shipVerticesTriangulated.get(i).mul(1 / PhysicsConstants.PIXEL_TO_METER_RATIO_DEFAULT);
        }

        shipMesh = new Mesh(x, y, shipMeshTriangles, shipVerticesTriangulated.size(),
                DrawMode.TRIANGLES, vbom
        );
        shipMesh.setColor(pColor);

        if (main) {
            mParticleEmitter = new PointParticleEmitter(x, y);
            mParticleSystem = new BatchedSpriteParticleSystem(
                    mParticleEmitter, 70, 100, 150, textureRegion, vbom
            );

            mExpireInitializer = new ExpireParticleInitializer<>(0);
            mVelocityInitializer = new VelocityParticleInitializer<>(0, 0);

            mParticleSystem.addParticleInitializer(mExpireInitializer);
            mParticleSystem.addParticleInitializer(
                    new ColorParticleInitializer<UncoloredSprite>(Color.RED)
            );

            mParticleSystem.addParticleModifier(
                    new ColorParticleModifier<UncoloredSprite>(0, 0.3f, Color.RED, Color.YELLOW)
            );
        }
    }

    public Ship(int x, int y, float shipSize, ITextureRegion textureRegion, boolean main,
                VertexBufferObjectManager vbom) {
        this(x, y, shipSize, Color.BLACK, textureRegion, main, vbom);
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
        shipBody.setTransform(shipBody.getPosition(), rotation);
    }

    public Mesh getShipMesh() {
        return shipMesh;
    }

    public Body getShipBody() {
        return shipBody;
    }

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================

    // ===========================================================
    // Methods
    // ===========================================================

    public void attachShip(Entity pScene, PhysicsWorld pPhysicsWorld){
        if (isMain)
            pScene.attachChild(mParticleSystem);

        pScene.attachChild(shipMesh);

        if (isMain) {
            shipBody = PhysicsFactory.createTrianglulatedBody(
                    pPhysicsWorld,
                    shipMesh,
                    shipVerticesTriangulated,
                    BodyDef.BodyType.DynamicBody,
                    SHIP_BODY_FICTURE_DEF
            );
            pPhysicsWorld.registerPhysicsConnector(new PhysicsConnector(shipMesh, shipBody));
            shipBody.setFixedRotation(true);
        }
    }

    public void applyForce(int i) {
        shipBody.applyForce(
                i * -(float)Math.sin((double)rotation),
                i * (float)Math.cos((double)rotation),
                shipBody.getPosition().x, shipBody.getPosition().y);
    }

    public boolean isContacted(Contact pContact){
        return pContact.getFixtureA().getBody().equals(shipBody) ||
               pContact.getFixtureB().getBody().equals(shipBody);
    }

    public void enableParticles(){
        if (isMain) {
            mParticleSystem.addParticleInitializer(mVelocityInitializer);
            mExpireInitializer.setLifeTime(0.3f, 0.4f);
        }
    }

    public void disableParticles(){
        mParticleSystem.removeParticleInitializer(mVelocityInitializer);
        mExpireInitializer.setLifeTime(0);
    }

    public void updateParticleSystem() {
        if (isMain) {
            mVelocityInitializer.setVelocityX(
                    (float) (Math.sin(rotation) * 500 - Math.cos(rotation) * 200),
                    (float) (Math.sin(rotation) * 500 + Math.cos(rotation) * 200));
            mVelocityInitializer.setVelocityY(
                    (float) (Math.cos(rotation) * -500 - Math.sin(rotation) * 200),
                    (float) (Math.cos(rotation) * -500 + Math.sin(rotation) * 200));

            mParticleEmitter.setCenter(shipMesh.getX() - 10, shipMesh.getY() - 10);
        }
    }

    public void setPosition(float x, float y, float rotation) {
        shipMesh.setPosition(x, y);
        shipMesh.setRotation(rotation * -180 / (float)Math.PI);
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
